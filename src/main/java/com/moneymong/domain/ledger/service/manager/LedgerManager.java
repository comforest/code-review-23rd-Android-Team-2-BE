package com.moneymong.domain.ledger.service.manager;

import com.moneymong.domain.agency.entity.AgencyUser;
import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import com.moneymong.domain.agency.service.AgencyService;
import com.moneymong.domain.ledger.api.request.CreateLedgerRequest;
import com.moneymong.domain.ledger.api.request.UpdateLedgerRequest;
import com.moneymong.domain.ledger.api.response.LedgerDetailInfoView;
import com.moneymong.domain.ledger.entity.Ledger;
import com.moneymong.domain.ledger.entity.LedgerDetail;
import com.moneymong.domain.ledger.entity.LedgerDocument;
import com.moneymong.domain.ledger.entity.LedgerReceipt;
import com.moneymong.domain.ledger.repository.LedgerDetailRepository;
import com.moneymong.domain.ledger.repository.LedgerRepository;
import com.moneymong.domain.user.entity.User;
import com.moneymong.domain.user.service.UserService;
import com.moneymong.global.exception.custom.BadRequestException;
import com.moneymong.global.exception.custom.InvalidAccessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.utils.AmountCalculatorByFundType;
import com.moneymong.utils.ModificationAmountCalculator;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerManager {
    private final UserService userService;
    private final AgencyService agencyService;
    private final LedgerDetailManager ledgerDetailManager;
    private final LedgerReceiptManager ledgerReceiptManager;
    private final LedgerDocumentManager ledgerDocumentManager;
    private final LedgerRepository ledgerRepository;
    private final LedgerDetailRepository ledgerDetailRepository;

    @Transactional
    public LedgerDetailInfoView createLedger(
            final Long userId,
            final Long ledgerId,
            final CreateLedgerRequest createLedgerRequest
    ) {
        // 1. 유저 검증
        User user = userService.validateUser(userId);

        Ledger ledger = ledgerRepository
                .findById(ledgerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.LEDGER_NOT_FOUND));

        // 2. 유저 소속 검증
        AgencyUser agencyUser = agencyService.validateAgencyUser(
                userId,
                ledger.getAgency().getId()
        );

        // 3. 유저 권한 검증
        if (!agencyUser.getAgencyUserRole().equals(AgencyUserRole.STAFF)) {
            throw new InvalidAccessException(ErrorCode.INVALID_LEDGER_ACCESS);
        }

        // 4. 장부 totalBalance 업데이트
        Ledger updateLedger = updateLedgerTotalBalance(
                AmountCalculatorByFundType.calculate(
                        createLedgerRequest.getFundType(),
                        createLedgerRequest.getAmount()
                ),
                ledger
        );

        // 5. 장부 내역 등록
        LedgerDetail ledgerDetail = ledgerDetailManager.createLedgerDetail(
                updateLedger,
                user,
                createLedgerRequest.getStoreInfo(),
                createLedgerRequest.getFundType(),
                createLedgerRequest.getAmount(),
                updateLedger.getTotalBalance(),
                createLedgerRequest.getDescription(),
                createLedgerRequest.getPaymentDate()
        );

        // 6. 장부 영수증 등록
        List<LedgerReceipt> ledgerReceipts = List.of();
        List<String> requestReceiptImageUrls = createLedgerRequest.getReceiptImageUrls();
        if (requestReceiptImageUrls.size() > 0) {
            ledgerReceipts = ledgerReceiptManager.createReceipts(
                    ledgerDetail.getId(),
                    requestReceiptImageUrls
            );
        }

        // 8. 장부 증빙 자료 등록
        List<LedgerDocument> ledgerDocuments = List.of();
        List<String> requestDocumentImageUrls = createLedgerRequest.getDocumentImageUrls();
        if (requestDocumentImageUrls.size() > 0) {
            ledgerDocuments = ledgerDocumentManager.createLedgerDocuments(
                    ledgerDetail.getId(),
                    requestDocumentImageUrls
            );
        }
        return LedgerDetailInfoView.of(
                ledgerDetail,
                ledgerReceipts,
                ledgerDocuments,
                user
        );
    }

    @Transactional
    public LedgerDetailInfoView updateLedger(
            final Long userId,
            final Long ledgerDetailId,
            final UpdateLedgerRequest updateLedgerRequest
    ) {
        // 1. 유저 검증
        User user = userService.validateUser(userId);

        // 2. 장부 상세 내역 검증
        LedgerDetail ledgerDetail = ledgerDetailRepository
                .findById(ledgerDetailId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.LEDGER_DETAIL_NOT_FOUND));

        Ledger ledger = ledgerDetail.getLedger();

        // 3. 유저 소속 검증
        AgencyUser agencyUser = agencyService.validateAgencyUser(
                userId,
                ledger.getAgency().getId()
        );

        // 4. 유저 권한 검증
        if (!agencyUser.getAgencyUserRole().equals(AgencyUserRole.STAFF)) {
            throw new InvalidAccessException(ErrorCode.INVALID_LEDGER_ACCESS);
        }


        // 6. 장부 총 잔액 업데이트
        final Integer newAmount = ModificationAmountCalculator.calculate(
                ledgerDetail.getFundType(),
                ledgerDetail.getAmount(),
                updateLedgerRequest.getAmount()
        );

        ledger = updateLedgerTotalBalance(newAmount, ledger);

        // 7. 장부 상세 내역 정보 업데이트
        return ledgerDetailManager.updateLedgerDetail(
                user,
                ledger,
                ledgerDetail,
                updateLedgerRequest
        );
    }

    private Ledger updateLedgerTotalBalance(
            final Integer newAmount,
            final Ledger ledger
    ) {

        BigDecimal expectedAmount = new BigDecimal(ledger.getTotalBalance())
                .add(new BigDecimal(newAmount));

        // 7. 장부 금액 최소 초과 검증
        BigDecimal minValue = BigDecimal.ZERO;
        BigDecimal maxValue = new BigDecimal("999999999");
        if (!(expectedAmount.compareTo(minValue) >= 0 &&
                expectedAmount.compareTo(maxValue) <= 0)
        ) {
            throw new BadRequestException(ErrorCode.INVALID_LEDGER_AMOUNT);
        }

        ledger.updateTotalBalance(expectedAmount.intValue());
        return ledgerRepository.save(ledger);
    }
}
