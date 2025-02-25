package com.moneymong.domain.ledger.api;

import com.moneymong.domain.ledger.api.response.LedgerDetailInfoView;
import com.moneymong.domain.ledger.service.manager.LedgerDetailManager;
import com.moneymong.domain.ledger.service.reader.LedgerDetailReader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "6. [장부 상세 내역]")
@RequestMapping("/api/v1/ledger-detail")
@RestController
@RequiredArgsConstructor
public class LedgerDetailController {

    private final LedgerDetailReader ledgerDetailReader;
    private final LedgerDetailManager ledgerDetailManager;

    @Operation(summary = "장부 상세 내역 조회 API")
    @GetMapping("/{id}")
    public LedgerDetailInfoView getLedgerDetailInfo(
            // @AuthenticationPrincipal ..
            @PathVariable("id") final Long ledgerDetailId
    ) {
        return ledgerDetailReader.getLedgerDetail(1L, ledgerDetailId);
    }

    @Operation(summary = "장부 상세 내역 삭제 API")
    @DeleteMapping("/{detailId}")
    public void removeLedgerDetailInfo(
            // @AuthenticationPrincipal
            @PathVariable("detailId") final Long detailId
    ) {
        ledgerDetailManager.removeLedgerDetail(1L, detailId);
    }

}
