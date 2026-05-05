package com.workforcehub.file.dto;

import lombok.Data;

@Data
public class CleanupJobRunResponse {

    private String jobRunId;
    private String jobType;
    private Boolean dryRun;
    private Long expiredUploadTickets;
    private Long expiredDownloadTickets;
    private Long purgedAttachmentRefs;
    private Long deletedPhysicalObjects;
    private Long releasedBytes;
}
