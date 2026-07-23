package com.cinema.user_service.services.audit;

import com.cinema.dto.request.SendEmailRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.util.List;

@Service
public class UserAuditEmailService {

    public SendEmailRequest buildAuditEmail(
            String to,
            String subject,
            String header,
            String actorName,
            String actorRole,
            String targetName,
            String targetRole,
            String action,
            List<String> fieldChanges) {

        String content = buildContent(actorName, actorRole, targetName, targetRole, action, fieldChanges);
        return new SendEmailRequest(to, subject, header, content);
    }

    private String buildContent(
            String actorName,
            String actorRole,
            String targetName,
            String targetRole,
            String action,
            List<String> fieldChanges) {

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Arial,sans-serif;line-height:1.6\">");
        html.append("<p><strong>Người thao tác:</strong> ")
                .append(escape(displayValue(actorName)))
                .append(" (")
                .append(escape(displayValue(actorRole)))
                .append(")</p>");
        html.append("<p><strong>Người bị tác động:</strong> ")
                .append(escape(displayValue(targetName)))
                .append(" (")
                .append(escape(displayValue(targetRole)))
                .append(")</p>");
        html.append("<p><strong>Thao tác:</strong> ")
                .append(escape(displayValue(action)))
                .append("</p>");

        if (fieldChanges == null || fieldChanges.isEmpty()) {
            html.append("<p>Không có thay đổi dữ liệu.</p>");
        } else {
            html.append("<p><strong>Field thay đổi:</strong></p><ul>");
            for (String change : fieldChanges) {
                html.append("<li>").append(escape(displayValue(change))).append("</li>");
            }
            html.append("</ul>");
        }

        html.append("</div>");
        return html.toString();
    }

    private String displayValue(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
