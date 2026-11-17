package com.mall.payment.service;

import com.mall.payment.common.PageResult;
import com.mall.payment.dto.request.ArbitrateRequest;
import com.mall.payment.dto.request.CreateAfterSaleRequest;
import com.mall.payment.dto.request.SupplementRequest;
import com.mall.payment.dto.response.AfterSaleResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 分类型售后：仅退款、退货退款、换货。
 *
 * <p>#143 增量：凭证上传、买家撤销、进度时间线、平台仲裁与双方补充材料。
 */
public interface AfterSaleService {

    AfterSaleResponse create(String buyerId, String idempotencyKey, CreateAfterSaleRequest request);

    PageResult<AfterSaleResponse> list(String userId, String role, String status, String type,
                                       String orderId, int page, int size);

    AfterSaleResponse detail(String userId, String role, String ticketId);

    AfterSaleResponse audit(String ticketId, String operatorId, String role,
                            boolean approved, String remark);

    AfterSaleResponse shipReturn(String ticketId, String buyerId, String trackingNo);

    AfterSaleResponse receiveReturn(String ticketId, String operatorId, String role, String remark);

    AfterSaleResponse reshipExchange(String ticketId, String operatorId, String role,
                                     String trackingNo, String remark);

    /** #143 买家撤销：仅 PENDING 可撤销，终态 CANCELLED */
    AfterSaleResponse cancel(String ticketId, String buyerId, String remark);

    /** #143 买家申请平台仲裁：仅卖家 REJECTED 后可申请，进入 ARBITRATING 锁定 */
    AfterSaleResponse requestArbitration(String ticketId, String buyerId, String remark);

    /** #143 仲裁期间双方补充说明与凭证，材料对双方可见 */
    AfterSaleResponse supplement(String ticketId, String userId, String role, SupplementRequest request);

    /** #143 管理员裁定：REFUND 联动退款，REJECT 驳回；仅 ADMIN，理由必填 */
    AfterSaleResponse arbitrate(String ticketId, String operatorId, String role, ArbitrateRequest request);

    /** #143 上传凭证（先上传后引用），单次最多 3 张，返回站内路径 */
    List<String> uploadAttachments(String userId, List<MultipartFile> files);
}