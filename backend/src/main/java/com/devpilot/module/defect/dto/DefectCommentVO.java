package com.devpilot.module.defect.dto;

import com.devpilot.module.user.dto.UserVO;

import java.time.LocalDateTime;

public record DefectCommentVO(Long id, Long defectId, Long authorId, String authorName,
                              String content, LocalDateTime createdAt) {

    public static DefectCommentVO from(com.devpilot.module.defect.DefectComment comment, String authorName) {
        return new DefectCommentVO(comment.getId(), comment.getDefectId(), comment.getAuthorId(),
                authorName, comment.getContent(), comment.getCreatedAt());
    }
}
