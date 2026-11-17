package com.mall.payment.service;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ChannelType;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.IdGenerator;
import com.mall.payment.common.PageResult;
import com.mall.payment.entity.Payment;
import com.mall.payment.entity.ReconcileBatch;
import com.mall.payment.entity.ReconcileDifference;
import com.mall.payment.entity.Refund;
import com.mall.payment.mapper.ChannelReconcileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 渠道对账（#137）：导入渠道账单 CSV，逐笔比对本地 payment/refund。
 *
 * <p>差异三类：LOCAL_ONLY（本地有账单无）/ CHANNEL_ONLY（账单有本地无）/
 * AMOUNT_MISMATCH（双方都有但金额不一致）。只落差异清单，
 * 全程只读 payment/refund，不自动修账。
 *
 * <p>CSV 格式（首行表头，逗号分隔，不支持引号转义）：
 * {@code trade_no,bill_type,out_trade_no,amount_fen,status,trade_time}
 * bill_type 取 PAYMENT/REFUND；只比对 status=SUCCESS 的行；
 * PAYMENT 行按 out_trade_no=pay_no 匹配，REFUND 行按
 * trade_no=channel_refund_no 或 out_trade_no=refund_id 匹配。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelReconcileService {

    private static final int MAX_ROWS = 10000;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String HEADER = "trade_no,bill_type,out_trade_no,amount_fen,status,trade_time";

    private final ChannelReconcileMapper reconcileMapper;

    @Transactional(rollbackFor = Exception.class)
    public ReconcileBatch importBill(String adminId, ChannelType channel, String fileName, InputStream csv) {
        List<BillRow> rows = parseCsv(csv);

        Map<String, Payment> paymentsByPayNo = new HashMap<>();
        for (Payment payment : reconcileMapper.selectPaidPaymentsByChannel(channel.name())) {
            paymentsByPayNo.put(payment.getPayNo(), payment);
        }
        Map<String, Refund> refundsByChannelNo = new HashMap<>();
        Map<String, Refund> refundsById = new HashMap<>();
        for (Refund refund : reconcileMapper.selectSuccessRefundsByChannel(channel.name())) {
            refundsById.put(refund.getRefundId(), refund);
            if (refund.getChannelRefundNo() != null && !refund.getChannelRefundNo().isBlank()) {
                refundsByChannelNo.put(refund.getChannelRefundNo(), refund);
            }
        }

        String batchId = IdGenerator.reconcileBatchId();
        List<ReconcileDifference> differences = new ArrayList<>();
        Set<String> matchedPayNos = new HashSet<>();
        Set<String> matchedRefundIds = new HashSet<>();

        for (BillRow row : rows) {
            if ("PAYMENT".equals(row.billType)) {
                Payment local = paymentsByPayNo.get(row.outTradeNo);
                if (local == null) {
                    differences.add(difference(batchId, channel, row, "CHANNEL_ONLY", null, null));
                } else {
                    matchedPayNos.add(local.getPayNo());
                    if (!local.getAmount().equals(row.amountFen)) {
                        differences.add(difference(batchId, channel, row, "AMOUNT_MISMATCH",
                                local.getPayNo(), local.getAmount()));
                    }
                }
            } else {
                Refund local = refundsByChannelNo.get(row.tradeNo);
                if (local == null) {
                    local = refundsById.get(row.outTradeNo);
                }
                if (local == null) {
                    differences.add(difference(batchId, channel, row, "CHANNEL_ONLY", null, null));
                } else {
                    matchedRefundIds.add(local.getRefundId());
                    if (!local.getAmount().equals(row.amountFen)) {
                        differences.add(difference(batchId, channel, row, "AMOUNT_MISMATCH",
                                local.getRefundId(), local.getAmount()));
                    }
                }
            }
        }

        for (Payment payment : paymentsByPayNo.values()) {
            if (!matchedPayNos.contains(payment.getPayNo())) {
                ReconcileDifference diff = new ReconcileDifference();
                diff.setBatchId(batchId);
                diff.setChannel(channel.name());
                diff.setBillType("PAYMENT");
                diff.setDiffType("LOCAL_ONLY");
                diff.setLocalNo(payment.getPayNo());
                diff.setLocalAmount(payment.getAmount());
                differences.add(diff);
            }
        }
        for (Refund refund : refundsById.values()) {
            if (!matchedRefundIds.contains(refund.getRefundId())) {
                ReconcileDifference diff = new ReconcileDifference();
                diff.setBatchId(batchId);
                diff.setChannel(channel.name());
                diff.setBillType("REFUND");
                diff.setDiffType("LOCAL_ONLY");
                diff.setLocalNo(refund.getRefundId());
                diff.setLocalAmount(refund.getAmount());
                differences.add(diff);
            }
        }

        ReconcileBatch batch = new ReconcileBatch();
        batch.setBatchId(batchId);
        batch.setChannel(channel.name());
        batch.setFileName(fileName);
        batch.setTotalRows(rows.size());
        batch.setDiffCount(differences.size());
        batch.setImportedBy(adminId);
        reconcileMapper.insertBatch(batch);
        for (ReconcileDifference diff : differences) {
            reconcileMapper.insertDifference(diff);
        }
        log.info("渠道对账完成, batchId={}, channel={}, rows={}, differences={}",
                batchId, channel, rows.size(), differences.size());
        return batch;
    }

    public PageResult<ReconcileBatch> listBatches(String channel, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String channelFilter = normalizeChannelFilter(channel);
        long total = reconcileMapper.countBatches(channelFilter);
        List<ReconcileBatch> list =
                reconcileMapper.selectBatches(channelFilter, (safePage - 1) * safeSize, safeSize);
        return PageResult.of(list, total, safePage, safeSize);
    }

    public PageResult<ReconcileDifference> listDifferences(String batchId, String diffType,
                                                           String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String typeFilter = normalize(diffType, Set.of("LOCAL_ONLY", "CHANNEL_ONLY", "AMOUNT_MISMATCH"),
                "差异类型不合法");
        String statusFilter = normalize(status, Set.of("PENDING", "RESOLVED"), "差异状态不合法");
        long total = reconcileMapper.countDifferences(batchId, typeFilter, statusFilter);
        List<ReconcileDifference> list = reconcileMapper.selectDifferences(
                batchId, typeFilter, statusFilter, (safePage - 1) * safeSize, safeSize);
        return PageResult.of(list, total, safePage, safeSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReconcileDifference resolve(Long differenceId, String adminId, String remark) {
        ReconcileDifference existing = reconcileMapper.selectDifferenceById(differenceId);
        if (existing == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "差异记录不存在");
        }
        int updated = reconcileMapper.resolveDifference(differenceId, adminId, remark, LocalDateTime.now());
        if (updated == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "差异已处理，请刷新后查看");
        }
        return reconcileMapper.selectDifferenceById(differenceId);
    }

    /* ---------- 内部 ---------- */

    private record BillRow(String tradeNo, String billType, String outTradeNo, long amountFen) {
    }

    private List<BillRow> parseCsv(InputStream csv) {
        List<BillRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !HEADER.equals(header.trim().toLowerCase(Locale.ROOT))) {
                throw new BizException(ErrorCode.PARAM_INVALID, "账单表头必须是: " + HEADER);
            }
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(",", -1);
                if (parts.length != 6) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "账单第 " + lineNo + " 行列数不是 6");
                }
                String status = parts[4].trim().toUpperCase(Locale.ROOT);
                if (!"SUCCESS".equals(status)) {
                    continue;
                }
                String billType = parts[1].trim().toUpperCase(Locale.ROOT);
                if (!"PAYMENT".equals(billType) && !"REFUND".equals(billType)) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "账单第 " + lineNo + " 行 bill_type 必须是 PAYMENT/REFUND");
                }
                long amount;
                try {
                    amount = Long.parseLong(parts[3].trim());
                } catch (NumberFormatException e) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "账单第 " + lineNo + " 行金额不是整数分");
                }
                if (amount < 0) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "账单第 " + lineNo + " 行金额为负");
                }
                rows.add(new BillRow(parts[0].trim(), billType, parts[2].trim(), amount));
                if (rows.size() > MAX_ROWS) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "账单行数超过 " + MAX_ROWS + " 上限");
                }
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账单读取失败");
        }
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账单没有可比对的 SUCCESS 记录");
        }
        return rows;
    }

    private ReconcileDifference difference(String batchId, ChannelType channel, BillRow row,
                                           String diffType, String localNo, Long localAmount) {
        ReconcileDifference diff = new ReconcileDifference();
        diff.setBatchId(batchId);
        diff.setChannel(channel.name());
        diff.setBillType(row.billType);
        diff.setDiffType(diffType);
        diff.setBillTradeNo(row.tradeNo);
        diff.setLocalNo(localNo);
        diff.setLocalAmount(localAmount);
        diff.setChannelAmount(row.amountFen);
        return diff;
    }

    private String normalizeChannelFilter(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        try {
            return ChannelType.valueOf(channel.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "渠道不合法");
        }
    }

    private String normalize(String value, Set<String> allowed, String errorMessage) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (!allowed.contains(upper)) {
            throw new BizException(ErrorCode.PARAM_INVALID, errorMessage);
        }
        return upper;
    }
}