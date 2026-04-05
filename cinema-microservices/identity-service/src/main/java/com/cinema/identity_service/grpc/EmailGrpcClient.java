package com.cinema.identity_service.grpc;

import com.cinema.dto.request.SendEmailRequest;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.common.OperationReply;
import com.cinema.grpc.email.EmailInternalServiceGrpc;
import com.cinema.grpc.email.SendEmailGrpcRequest;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailGrpcClient {

    private final EmailInternalServiceGrpc.EmailInternalServiceBlockingStub emailBlockingStub;

    public EmailGrpcClient(GrpcChannelFactory channelFactory) {
        this.emailBlockingStub = EmailInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("email"));
    }

    public void sendEmail(SendEmailRequest request) {
        try {
            OperationReply reply = emailBlockingStub.sendEmail(SendEmailGrpcRequest.newBuilder()
                    .setTo(request.getTo())
                    .setSubject(request.getSubject())
                    .setHeader(request.getHeader())
                    .setContent(request.getContent())
                    .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EMAIL_SEND_FAILED));
            }
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }
}
