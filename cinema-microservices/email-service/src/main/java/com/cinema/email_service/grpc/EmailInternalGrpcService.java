package com.cinema.email_service.grpc;

import com.cinema.dto.request.SendEmailRequest;
import com.cinema.email_service.services.EmailService;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.common.OperationReply;
import com.cinema.grpc.email.EmailInternalServiceGrpc;
import com.cinema.grpc.email.SendEmailGrpcRequest;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailInternalGrpcService extends EmailInternalServiceGrpc.EmailInternalServiceImplBase implements BindableService {

    private final EmailService emailService;

    @Override
    public void sendEmail(
            SendEmailGrpcRequest request,
            StreamObserver<OperationReply> responseObserver) {
        try {
            emailService.sendEmail(new SendEmailRequest(
                    request.getTo(),
                    request.getSubject(),
                    request.getHeader(),
                    request.getContent()));

            responseObserver.onNext(OperationReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Email dispatch accepted")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(OperationReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while sending email", ex);
            responseObserver.onNext(OperationReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }
}
