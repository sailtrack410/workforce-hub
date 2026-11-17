package com.workforcehub.ragflow.service;

import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.repository.AttachmentRow;
import com.workforcehub.ragflow.dto.AddRagflowDocumentRequest;
import com.workforcehub.ragflow.dto.CreateRagflowSpaceRequest;
import com.workforcehub.ragflow.dto.RagflowAclResponse;
import com.workforcehub.ragflow.dto.RagflowDocumentResponse;
import com.workforcehub.ragflow.dto.RagflowGlobalSettingResponse;
import com.workforcehub.ragflow.dto.RagflowSearchTestRequest;
import com.workforcehub.ragflow.dto.RagflowSearchTestResponse;
import com.workforcehub.ragflow.dto.RagflowSpaceResponse;
import com.workforcehub.ragflow.dto.SaveRagflowAclRequest;
import com.workforcehub.ragflow.dto.UpdateRagflowGlobalSettingRequest;
import com.workforcehub.ragflow.dto.UpdateRagflowSpaceRequest;
import com.workforcehub.ragflow.repository.RagflowAclRow;
import com.workforcehub.ragflow.repository.RagflowDocumentRow;
import com.workforcehub.ragflow.repository.RagflowGlobalSettingRow;
import com.workforcehub.ragflow.repository.RagflowRepository;
import com.workforcehub.ragflow.repository.RagflowSearchHitRow;
import com.workforcehub.ragflow.repository.RagflowSpaceRow;
import com.workforcehub.ragflow.repository.RagflowVectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

import static com.workforcehub.common.util.TextValues.trimToNull;

@Service
public class RagflowAdminService {

    private static final Logger log = LoggerFactory.getLogger(RagflowAdminService.class);

    // 知识库可见范围枚举：PRIVATE 仅自己、ROLE/DEPARTMENT/POSITION 按组织维度、PUBLIC 全员可见
    private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "ROLE", "DEPARTMENT", "POSITION", "PUBLIC");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> SUBJECT_TYPES = Set.of("ROLE", "DEPARTMENT", "POSITION");
    private static final Set<String> PERMISSIONS = Set.of("read", "manage");

    private final RagflowRepository ragflowRepository;
    private final AttachmentRefRepository attachmentRefRepository;
    private final RagflowIngestService ragflowIngestService;
    private final RagflowKnowledgeSearchService knowledgeSearchService;
    private final RagflowConfigResolver configResolver;
    private final RagflowEmbeddingClient embeddingClient;
    private final RagflowVectorRepository vectorRepository;
    private final Executor kbIngestExecutor;

    @Value("${workforce.ragflow.embedding.validate-on-create:true}")
    private boolean validateEmbeddingOnCreate = true;

    public RagflowAdminService(RagflowRepository ragflowRepository,
                               AttachmentRefRepository attachmentRefRepository,
                               RagflowIngestService ragflowIngestService,
                               RagflowKnowledgeSearchService knowledgeSearchService,
                               RagflowConfigResolver configResolver,
                               RagflowEmbeddingClient embeddingClient,
                               RagflowVectorRepository vectorRepository,
                               @Qualifier("kbIngestExecutor") Executor kbIngestExecutor) {
        this.ragflowRepository = ragflowRepository;
        this.attachmentRefRepository = attachmentRefRepository;
        this.ragflowIngestService = ragflowIngestService;
        this.knowledgeSearchService = knowledgeSearchService;
        this.configResolver = configResolver;
        this.embeddingClient = embeddingClient;
        this.vectorRepository = vectorRepository;
        this.kbIngestExecutor = kbIngestExecutor;
    }

    public PageResult<RagflowSpaceResponse> pageSpaces(long pageNo, long pageSize, String keyword) {
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.min(Math.max(pageSize, 1L), 100L);
        long total = ragflowRepository.countSpaces(keyword);
        List<RagflowSpaceResponse> rows = ragflowRepository.pageSpaces(keyword, (safePageNo - 1) * safePageSize, safePageSize).stream()
            .map(this::toSpaceResponse)
            .toList();
        return PageResult.of(safePageNo, safePageSize, total, rows);
    }

    public RagflowSpaceResponse getSpace(Long spaceId) {
        return toSpaceResponse(ragflowRepository.getSpaceOrThrow(spaceId));
    }

    public RagflowGlobalSettingResponse getGlobalSetting() {
        return toGlobalSettingResponse(ragflowRepository.getGlobalSetting());
    }

    @Transactional(rollbackFor = Exception.class)
    public RagflowGlobalSettingResponse updateGlobalSetting(UpdateRagflowGlobalSettingRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        RagflowGlobalSettingRow existing = ragflowRepository.getGlobalSetting();
        Integer embeddingDimension = detectGlobalEmbeddingDimension(request, existing);
        ragflowRepository.updateGlobalSetting(
            trimToNull(request.getOcrBaseUrl()),
            trimToNull(request.getOcrApiKey()),
            trimToNull(request.getOcrModel()),
            request.getOcrModelId(),
            trimToNull(request.getEmbeddingBaseUrl()),
            trimToNull(request.getEmbeddingApiKey()),
            trimToNull(request.getEmbeddingModel()),
            request.getEmbeddingModelId(),
            embeddingDimension,
            request.getRerankEnabled(),
            trimToNull(request.getRerankBaseUrl()),
            trimToNull(request.getRerankApiKey()),
            trimToNull(request.getRerankModel()),
            request.getRerankModelId(),
            request.getChunkSize(),
            request.getChunkOverlap(),
            request.getRetrievalTopK(),
            normalizeScoreThreshold(request.getScoreThreshold()),
            operatorId
        );
        return getGlobalSetting();
    }

    @Transactional(rollbackFor = Exception.class)
    public RagflowSpaceResponse createSpace(CreateRagflowSpaceRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        String spaceCode = trimRequired(request.getSpaceCode(), "知识库编码不能为空").toUpperCase(Locale.ROOT);
        if (ragflowRepository.existsSpaceCode(spaceCode)) {
            throw new BizException(409, "知识库编码已存在");
        }
        RagflowGlobalSettingRow setting = ragflowRepository.getGlobalSetting();
        Long embeddingModelId = request.getEmbeddingModelId() == null ? setting.embeddingModelId() : request.getEmbeddingModelId();
        Integer embeddingDimension = request.getEmbeddingDimension() == null ? setting.embeddingDimension() : request.getEmbeddingDimension();
        if (validateEmbeddingOnCreate) {
            int detectedDimension = embeddingClient.detectDimension(embeddingModelId);
            if (request.getEmbeddingDimension() != null && request.getEmbeddingDimension() != detectedDimension) {
                throw new BizException(422, "该Embedding模型实际返回" + detectedDimension + "维，不能配置为" + request.getEmbeddingDimension() + "维");
            }
            embeddingDimension = detectedDimension;
        }
        Long spaceId = IdGenerator.nextId();
        ragflowRepository.insertSpace(
            spaceId,
            trimRequired(request.getSpaceName(), "知识库名称不能为空"),
            spaceCode,
            trimToNull(request.getDescription()),
            normalizeDefault(request.getVisibility(), "PRIVATE", VISIBILITIES, "可见范围不支持"),
            normalizeDefault(request.getStatus(), "ACTIVE", STATUSES, "状态不支持"),
            trimToNull(request.getEmbeddingProvider()),
            trimToNull(request.getEmbeddingModel()),
            embeddingModelId,
            embeddingDimension,
            request.getRerankEnabled(),
            trimToNull(request.getRerankProvider()),
            trimToNull(request.getRerankModel()),
            request.getRerankModelId(),
            request.getChunkSize() == null ? defaultInt(setting.chunkSize(), 800) : request.getChunkSize(),
            request.getChunkOverlap() == null ? defaultInt(setting.chunkOverlap(), 100) : request.getChunkOverlap(),
            request.getRetrievalTopK() == null ? defaultInt(setting.retrievalTopK(), 5) : request.getRetrievalTopK(),
            normalizeScoreThreshold(request.getScoreThreshold()),
            operatorId
        );
        return getSpace(spaceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public RagflowSpaceResponse updateSpace(Long spaceId, UpdateRagflowSpaceRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        RagflowSpaceRow existing = ragflowRepository.getSpaceOrThrow(spaceId);
        String embeddingProvider = trimToNull(request.getEmbeddingProvider());
        String embeddingModel = trimToNull(request.getEmbeddingModel());
        ensureVectorSettingsUnchanged(existing, embeddingProvider, embeddingModel, request);
        ragflowRepository.updateSpace(
            spaceId,
            trimRequired(request.getSpaceName(), "知识库名称不能为空"),
            trimToNull(request.getDescription()),
            normalizeDefault(request.getVisibility(), "PRIVATE", VISIBILITIES, "可见范围不支持"),
            normalizeDefault(request.getStatus(), "ACTIVE", STATUSES, "状态不支持"),
            existing.embeddingProvider(),
            existing.embeddingModel(),
            existing.embeddingModelId(),
            existing.embeddingDimension(),
            request.getRerankEnabled(),
            trimToNull(request.getRerankProvider()),
            trimToNull(request.getRerankModel()),
            request.getRerankModelId(),
            existing.chunkSize(),
            existing.chunkOverlap(),
            request.getRetrievalTopK(),
            normalizeScoreThreshold(request.getScoreThreshold()),
            operatorId
        );
        return getSpace(spaceId);
    }

    public List<RagflowDocumentResponse> listDocuments(Long spaceId) {
        ragflowRepository.getSpaceOrThrow(spaceId);
        return ragflowRepository.listDocuments(spaceId).stream()
            .map(this::toDocumentResponse)
            .toList();
    }

    // 添加文档：先校验附件归属，再创建文档记录和入库任务，最后异步触发向量化入库
    @Transactional(rollbackFor = Exception.class)
    public RagflowDocumentResponse addDocument(Long spaceId, AddRagflowDocumentRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        ragflowRepository.getSpaceOrThrow(spaceId);
        AttachmentRow attachment = attachmentRefRepository.getAttachmentOrThrow(request.getAttachmentRefId());
        if (!"RAGFLOW_DOCUMENT".equals(attachment.ownerType()) || !spaceId.equals(attachment.ownerId())) {
            throw new BizException(403, "附件不属于该知识库");
        }
        if (attachment.deletedAt() != null || attachment.isDeleted() != 0) {
            throw new BizException(409, "附件已删除");
        }
        if (ragflowRepository.existsDocumentByAttachment(spaceId, request.getAttachmentRefId())) {
            return listDocuments(spaceId).stream()
                .filter(item -> request.getAttachmentRefId().equals(item.getAttachmentRefId()))
                .findFirst()
                .orElseThrow(() -> new BizException(409, "文档已存在"));
        }
        Long documentId = IdGenerator.nextId();
        Long jobId = IdGenerator.nextId();
        ragflowRepository.insertDocument(
            documentId,
            spaceId,
            attachment.id(),
            attachment.fileObjectId(),
            attachment.displayName(),
            attachment.mimeType(),
            attachment.fileSize(),
            attachment.contentSha256(),
            parserType(attachment.mimeType(), attachment.displayName()),
            operatorId
        );
        ragflowRepository.insertIngestJob(jobId, documentId, spaceId, attachment.fileObjectId(), operatorId);
        ragflowRepository.bindLatestIngestJob(documentId, jobId, operatorId);
        scheduleIngest(
            spaceId,
            documentId,
            jobId,
            attachment.fileObjectId(),
            attachment.displayName(),
            attachment.mimeType(),
            parserType(attachment.mimeType(), attachment.displayName())
        );
        ragflowRepository.refreshSpaceCounts(spaceId);
        return listDocuments(spaceId).stream()
            .filter(item -> documentId.equals(item.getDocumentId()))
            .findFirst()
            .orElseThrow(() -> new BizException(500, "文档创建失败"));
    }

    @Transactional(rollbackFor = Exception.class)
    public RagflowDocumentResponse reingestDocument(Long spaceId, Long documentId) {
        Long operatorId = CurrentUserHolder.requireUserId();
        ragflowRepository.getSpaceOrThrow(spaceId);
        RagflowDocumentRow document = ragflowRepository.getDocumentOrThrow(spaceId, documentId);
        Long jobId = IdGenerator.nextId();
        ragflowRepository.insertIngestJob(jobId, documentId, spaceId, document.sourceFileObjectId(), operatorId);
        ragflowRepository.bindLatestIngestJob(documentId, jobId, operatorId);
        scheduleIngest(
            spaceId,
            documentId,
            jobId,
            document.sourceFileObjectId(),
            document.title(),
            document.mimeType(),
            document.parserType()
        );
        ragflowRepository.refreshSpaceCounts(spaceId);
        return listDocuments(spaceId).stream()
            .filter(item -> documentId.equals(item.getDocumentId()))
            .findFirst()
            .orElseThrow(() -> new BizException(500, "文档重新入库失败"));
    }

    private void scheduleIngest(Long spaceId, Long documentId, Long jobId, Long fileObjectId, String title, String mimeType, String parserType) {
        Runnable execution = () -> kbIngestExecutor.execute(() -> ragflowIngestService.ingest(
            spaceId,
            documentId,
            jobId,
            fileObjectId,
            title,
            mimeType,
            parserType
        ));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    execution.run();
                }
            });
            return;
        }
        execution.run();
    }

    // 删除知识库：软删除空间和文档，同时停用切片与向量，保留历史审计链路
    @Transactional(rollbackFor = Exception.class)
    public void deleteSpace(Long spaceId) {
        Long operatorId = CurrentUserHolder.requireUserId();
        ragflowRepository.getSpaceOrThrow(spaceId);
        ragflowRepository.softDeleteDocumentsBySpace(spaceId, operatorId);
        ragflowRepository.deactivateSpaceChunks(spaceId);
        ragflowRepository.deleteAcl(spaceId);
        ragflowRepository.softDeleteSpace(spaceId, operatorId);
        try {
            vectorRepository.deactivateSpaceVectors(spaceId);
        } catch (Exception | LinkageError e) {
            // 向量清理属于删除主流程的附带动作，失败仅记日志不回滚
            log.warn("停用空间向量失败 spaceId={}", spaceId, e);
        }
    }

    // 删除文档：软删除数据库记录 + 停用向量，向量清理失败不影响主流程
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long spaceId, Long documentId) {
        Long operatorId = CurrentUserHolder.requireUserId();
        ragflowRepository.getSpaceOrThrow(spaceId);
        ragflowRepository.getDocumentOrThrow(spaceId, documentId);
        ragflowRepository.softDeleteDocument(spaceId, documentId, operatorId);
        ragflowRepository.deactivateDocumentChunks(documentId);
        try {
            vectorRepository.deactivateDocumentVectors(documentId);
        } catch (Exception | LinkageError e) {
            log.warn("停用文档向量失败 documentId={}", documentId, e);
        }
        ragflowRepository.refreshSpaceCounts(spaceId);
    }

    public RagflowSearchTestResponse searchTest(Long spaceId, RagflowSearchTestRequest request) {
        RagflowSpaceRow space = ragflowRepository.getSpaceOrThrow(spaceId);
        String query = trimRequired(request.getQuery(), "搜索内容不能为空");
        RagflowEffectiveConfig config = configResolver.resolve(space, ragflowRepository.getGlobalSetting());
        int topK = defaultInt(config.retrievalTopK(), 5);
        List<RagflowSearchHitRow> rows = knowledgeSearchService.searchRows(spaceId, query, topK);
        RagflowSearchTestResponse response = new RagflowSearchTestResponse();
        response.setQuery(query);
        response.setHits(rows.stream().map(this::toSearchHit).toList());
        return response;
    }

    public List<RagflowAclResponse> listAcl(Long spaceId) {
        ragflowRepository.getSpaceOrThrow(spaceId);
        return ragflowRepository.listAcl(spaceId).stream()
            .map(this::toAclResponse)
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public List<RagflowAclResponse> saveAcl(Long spaceId, SaveRagflowAclRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        ragflowRepository.getSpaceOrThrow(spaceId);
        ragflowRepository.deleteAcl(spaceId);
        for (SaveRagflowAclRequest.Item item : request.getItems()) {
            if (item.getSubjectId() == null) {
                continue;
            }
            String subjectType = normalizeDefault(item.getSubjectType(), "", SUBJECT_TYPES, "授权主体类型不支持");
            String permCode = normalizePermission(item.getPermCode());
            ragflowRepository.insertAcl(IdGenerator.nextId(), spaceId, subjectType, item.getSubjectId(), permCode, operatorId);
        }
        return listAcl(spaceId);
    }

    private RagflowSpaceResponse toSpaceResponse(RagflowSpaceRow row) {
        RagflowSpaceResponse response = new RagflowSpaceResponse();
        response.setSpaceId(row.id());
        response.setSpaceName(row.spaceName());
        response.setSpaceCode(row.spaceCode());
        response.setDescription(row.description());
        response.setVisibility(row.visibility());
        response.setVisibilityName(visibilityName(row.visibility()));
        response.setDocumentCount(row.documentCount());
        response.setChunkCount(row.chunkCount());
        response.setEmbeddingProvider(row.embeddingProvider());
        response.setEmbeddingModel(row.embeddingModel());
        response.setEmbeddingModelId(row.embeddingModelId());
        response.setEmbeddingDimension(row.embeddingDimension());
        response.setRerankEnabled(row.rerankEnabled());
        response.setRerankProvider(row.rerankProvider());
        response.setRerankModel(row.rerankModel());
        response.setRerankModelId(row.rerankModelId());
        response.setChunkSize(row.chunkSize());
        response.setChunkOverlap(row.chunkOverlap());
        response.setRetrievalTopK(row.retrievalTopK());
        response.setScoreThreshold(row.scoreThreshold());
        response.setStatus(row.status());
        response.setStatusName(statusName(row.status()));
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private RagflowGlobalSettingResponse toGlobalSettingResponse(RagflowGlobalSettingRow row) {
        RagflowGlobalSettingResponse response = new RagflowGlobalSettingResponse();
        response.setSettingId(row.id());
        response.setOcrBaseUrl(row.ocrBaseUrl());
        response.setOcrApiKeyConfigured(row.ocrApiKeyConfigured());
        response.setOcrModel(row.ocrModel());
        response.setOcrModelId(row.ocrModelId());
        response.setEmbeddingBaseUrl(row.embeddingBaseUrl());
        response.setEmbeddingApiKeyConfigured(row.embeddingApiKeyConfigured());
        response.setEmbeddingModel(row.embeddingModel());
        response.setEmbeddingModelId(row.embeddingModelId());
        response.setEmbeddingDimension(row.embeddingDimension());
        response.setRerankEnabled(row.rerankEnabled());
        response.setRerankBaseUrl(row.rerankBaseUrl());
        response.setRerankApiKeyConfigured(row.rerankApiKeyConfigured());
        response.setRerankModel(row.rerankModel());
        response.setRerankModelId(row.rerankModelId());
        response.setChunkSize(row.chunkSize());
        response.setChunkOverlap(row.chunkOverlap());
        response.setRetrievalTopK(row.retrievalTopK());
        response.setScoreThreshold(row.scoreThreshold());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private void ensureVectorSettingsUnchanged(RagflowSpaceRow existing,
                                               String embeddingProvider,
                                               String embeddingModel,
                                               UpdateRagflowSpaceRequest request) {
        boolean changed = (embeddingProvider != null && !Objects.equals(trimToNull(existing.embeddingProvider()), embeddingProvider))
            || (embeddingModel != null && !Objects.equals(trimToNull(existing.embeddingModel()), embeddingModel))
            || (request.getEmbeddingModelId() != null && !Objects.equals(existing.embeddingModelId(), request.getEmbeddingModelId()))
            || (request.getEmbeddingDimension() != null && !Objects.equals(existing.embeddingDimension(), request.getEmbeddingDimension()))
            || (request.getChunkSize() != null && !Objects.equals(existing.chunkSize(), request.getChunkSize()))
            || (request.getChunkOverlap() != null && !Objects.equals(existing.chunkOverlap(), request.getChunkOverlap()));
        if (changed) {
            throw new BizException(409, "嵌入模型、向量维度和切片参数只能在创建知识库时确定；请新建知识库后重新入库");
        }
    }

    private Integer detectGlobalEmbeddingDimension(UpdateRagflowGlobalSettingRequest request, RagflowGlobalSettingRow existing) {
        Long modelId = request.getEmbeddingModelId();
        Integer configuredDimension = request.getEmbeddingDimension();
        Integer detectedDimension = null;
        if (modelId != null) {
            detectedDimension = embeddingClient.detectDimension(modelId);
        } else {
            String baseUrl = trimToNull(request.getEmbeddingBaseUrl());
            String modelName = trimToNull(request.getEmbeddingModel());
            String apiKey = trimToNull(request.getEmbeddingApiKey());
            if (apiKey == null && Boolean.TRUE.equals(existing.embeddingApiKeyConfigured())) {
                apiKey = ragflowRepository.getRawEmbeddingApiKey();
            }
            if (StringUtils.hasText(baseUrl) || StringUtils.hasText(modelName) || StringUtils.hasText(apiKey)) {
                detectedDimension = embeddingClient.detectDimension(baseUrl, apiKey, modelName);
            }
        }
        if (detectedDimension == null) {
            return configuredDimension;
        }
        if (configuredDimension != null && !Objects.equals(configuredDimension, detectedDimension)) {
            throw new BizException(422, "该Embedding模型实际返回" + detectedDimension + "维，不能配置为" + configuredDimension + "维");
        }
        return detectedDimension;
    }

    private RagflowDocumentResponse toDocumentResponse(RagflowDocumentRow row) {
        RagflowDocumentResponse response = new RagflowDocumentResponse();
        response.setDocumentId(row.id());
        response.setSpaceId(row.spaceId());
        response.setAttachmentRefId(row.attachmentRefId());
        response.setSourceFileObjectId(row.sourceFileObjectId());
        response.setTitle(row.title());
        response.setMimeType(row.mimeType());
        response.setFileSize(row.fileSize());
        response.setContentHash(row.contentHash());
        response.setParserType(row.parserType());
        response.setParserTypeName(parserTypeName(row.parserType()));
        response.setIngestStatus(row.ingestStatus());
        response.setIngestStatusName(statusName(row.ingestStatus()));
        response.setLatestIngestJobId(row.latestIngestJobId());
        response.setIngestJobStatus(row.ingestJobStatus());
        response.setIngestJobStatusName(statusName(row.ingestJobStatus()));
        response.setVectorStatus(row.vectorStatus());
        response.setVectorStatusName(statusName(row.vectorStatus()));
        response.setChunkCount(row.chunkCount());
        response.setErrorMessage(row.errorMessage());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private RagflowSearchTestResponse.Hit toSearchHit(RagflowSearchHitRow row) {
        RagflowSearchTestResponse.Hit hit = new RagflowSearchTestResponse.Hit();
        hit.setChunkId(row.chunkId());
        hit.setDocumentId(row.documentId());
        hit.setDocumentTitle(row.documentTitle());
        hit.setChunkNo(row.chunkNo());
        hit.setContentText(row.contentText());
        hit.setPageNo(row.pageNo());
        hit.setScore(row.score());
        return hit;
    }

    private RagflowAclResponse toAclResponse(RagflowAclRow row) {
        RagflowAclResponse response = new RagflowAclResponse();
        response.setAclId(row.id());
        response.setSpaceId(row.spaceId());
        response.setSubjectType(row.subjectType());
        response.setSubjectTypeName(subjectTypeName(row.subjectType()));
        response.setSubjectId(row.subjectId());
        response.setSubjectName(row.subjectName());
        response.setSubjectCode(row.subjectCode());
        response.setPermCode(row.permCode());
        response.setPermCodeName(permCodeName(row.permCode()));
        response.setStatus(row.status());
        response.setStatusName(statusName(row.status()));
        response.setCreatedAt(row.createdAt());
        return response;
    }

    private String visibilityName(String value) {
        if ("PRIVATE".equals(value)) return "私有";
        if ("DEPARTMENT".equals(value)) return "部门可见";
        if ("PUBLIC".equals(value)) return "全员可见";
        return value == null ? null : "其他范围";
    }

    private String parserTypeName(String value) {
        if ("AUTO".equals(value)) return "自动解析";
        if ("OCR".equals(value)) return "文字识别";
        if ("TEXT".equals(value)) return "文本解析";
        return value == null ? null : "其他解析";
    }

    private String subjectTypeName(String value) {
        if ("ROLE".equals(value)) return "角色";
        if ("DEPARTMENT".equals(value)) return "部门";
        if ("POSITION".equals(value)) return "岗位";
        if ("USER".equals(value)) return "人员";
        return value == null ? null : "授权对象";
    }

    private String permCodeName(String value) {
        if ("read".equalsIgnoreCase(value)) return "可查阅";
        if ("manage".equalsIgnoreCase(value)) return "可管理";
        if ("use".equalsIgnoreCase(value)) return "可使用";
        return value == null ? null : "业务权限";
    }

    private String statusName(String value) {
        if ("ACTIVE".equals(value)) return "启用";
        if ("INACTIVE".equals(value)) return "停用";
        if ("PENDING".equals(value)) return "待处理";
        if ("RUNNING".equals(value)) return "处理中";
        if ("SUCCESS".equals(value) || "SUCCEEDED".equals(value) || "DONE".equals(value)) return "已完成";
        if ("FAILED".equals(value)) return "失败";
        return value == null ? null : "其他状态";
    }

    private String trimRequired(String value, String message) {
        String trimmed = trimToNull(value);
        if (!StringUtils.hasText(trimmed)) {
            throw new BizException(400, message);
        }
        return trimmed;
    }

    private String normalizeDefault(String value, String fallback, Set<String> allowed, String message) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback;
        if (!allowed.contains(normalized)) {
            throw new BizException(400, message);
        }
        return normalized;
    }

    private String normalizePermission(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "read";
        if (!PERMISSIONS.contains(normalized)) {
            throw new BizException(400, "权限不支持");
        }
        return normalized;
    }

    private Integer defaultInt(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    private BigDecimal normalizeScoreThreshold(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new BizException(400, "分数阈值必须在0到1之间");
        }
        return value;
    }

    // 根据 MIME 类型和文件名后缀自动选择解析器：PDF/WORD/PPT/OCR/TABLE/TEXT
    private String parserType(String mimeType, String fileName) {
        String text = ((mimeType == null ? "" : mimeType) + " " + (fileName == null ? "" : fileName)).toLowerCase(Locale.ROOT);
        if (text.contains("pdf")) return "PDF";
        if (text.contains("word") || text.endsWith(".doc") || text.endsWith(".docx")) return "WORD";
        if (text.contains("presentation") || text.endsWith(".ppt") || text.endsWith(".pptx")) return "PPT";
        if (text.contains("image") || text.endsWith(".png") || text.endsWith(".jpg") || text.endsWith(".jpeg") || text.endsWith(".webp")) return "OCR";
        if (text.contains("spreadsheet") || text.contains("excel") || text.endsWith(".xls") || text.endsWith(".xlsx") || text.endsWith(".csv")) return "TABLE";
        return "TEXT";
    }
}
