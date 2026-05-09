package com.hify.knowledge.domain;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.web.PageResult;
import com.hify.knowledge.api.CreateKnowledgeBaseReq;
import com.hify.knowledge.api.KnowledgeBaseQuery;
import com.hify.knowledge.api.KnowledgeBaseResp;
import com.hify.knowledge.api.UpdateKnowledgeBaseReq;
import com.hify.knowledge.infra.KnowledgeBaseMapper;
import com.hify.knowledge.infra.KnowledgeDocumentMapper;
import com.hify.model.api.EmbeddingService;
import com.hify.model.api.ModelConfigResp;
import com.hify.model.api.ModelConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private KnowledgeDocumentMapper documentMapper;

    @Mock
    private KnowledgeVectorRepository vectorRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ThreadPoolExecutor asyncExecutor;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private ModelConfigService modelConfigService;

    @InjectMocks
    private KnowledgeServiceImpl knowledgeService;

    @Test
    void createKnowledgeBasePersistsDefaultFields() {
        CreateKnowledgeBaseReq req = new CreateKnowledgeBaseReq();
        req.setName("产品知识库");
        req.setDescription("内部产品文档");
        req.setEmbeddingModelConfigId(11L);

        ModelConfigResp embeddingModel = new ModelConfigResp();
        embeddingModel.setId(11L);
        embeddingModel.setEnabled(1);
        embeddingModel.setModelType("EMBEDDING");
        when(modelConfigService.getById(11L)).thenReturn(embeddingModel);

        KnowledgeBaseResp resp = knowledgeService.createKnowledgeBase(req);

        ArgumentCaptor<KnowledgeBasePo> captor = ArgumentCaptor.forClass(KnowledgeBasePo.class);
        verify(knowledgeBaseMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("产品知识库");
        assertThat(captor.getValue().getDescription()).isEqualTo("内部产品文档");
        assertThat(captor.getValue().getEmbeddingModelConfigId()).isEqualTo(11L);
        assertThat(captor.getValue().getEnabled()).isEqualTo(1);
        assertThat(captor.getValue().getDocumentCount()).isZero();
        assertThat(captor.getValue().getChunkCount()).isZero();
        assertThat(resp.getName()).isEqualTo("产品知识库");
        assertThat(resp.getEmbeddingModelConfigId()).isEqualTo(11L);
    }

    @Test
    void listKnowledgeBasesReturnsPageResult() {
        KnowledgeBasePo po = new KnowledgeBasePo();
        po.setId(1L);
        po.setName("产品知识库");
        po.setDescription("");
        po.setEnabled(1);
        po.setDocumentCount(2);
        po.setChunkCount(10);
        Page<KnowledgeBasePo> page = new Page<>(1, 20);
        page.setRecords(List.of(po));
        page.setTotal(1);
        when(knowledgeBaseMapper.selectPage(any(), any(Wrapper.class))).thenReturn(page);

        KnowledgeBaseQuery query = new KnowledgeBaseQuery();
        query.setName("产品");
        PageResult<KnowledgeBaseResp> result = knowledgeService.listKnowledgeBases(query);

        assertThat(result.getData().getTotal()).isEqualTo(1);
        assertThat(result.getData().getRecords().get(0).getName()).isEqualTo("产品知识库");
    }

    @Test
    void updateKnowledgeBaseChangesProvidedFields() {
        KnowledgeBasePo po = new KnowledgeBasePo();
        po.setId(1L);
        po.setName("旧名称");
        po.setDescription("旧描述");
        po.setEnabled(1);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(po);

        UpdateKnowledgeBaseReq req = new UpdateKnowledgeBaseReq();
        req.setName("新名称");
        req.setDescription("新描述");
        req.setEnabled(0);

        KnowledgeBaseResp resp = knowledgeService.updateKnowledgeBase(1L, req);

        verify(knowledgeBaseMapper).updateById(po);
        assertThat(resp.getName()).isEqualTo("新名称");
        assertThat(resp.getDescription()).isEqualTo("新描述");
        assertThat(resp.getEnabled()).isZero();
    }

    @Test
    void deleteKnowledgeBaseAlsoDeletesDocumentsAndVectorChunks() {
        KnowledgeBasePo po = new KnowledgeBasePo();
        po.setId(1L);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(po);

        knowledgeService.deleteKnowledgeBase(1L);

        verify(knowledgeBaseMapper).deleteById(1L);
        verify(documentMapper).delete(any(Wrapper.class));
        verify(vectorRepository).deleteByKnowledgeBaseId(1L);
    }
}
