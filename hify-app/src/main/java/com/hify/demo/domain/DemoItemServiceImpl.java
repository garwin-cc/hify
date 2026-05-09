package com.hify.demo.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.demo.infra.DemoItemMapper;
import com.hify.demo.infra.DemoItemPo;
import com.hify.demo.web.req.CreateDemoItemReq;
import com.hify.demo.web.req.UpdateDemoItemReq;
import com.hify.demo.web.resp.DemoItemResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DemoItemServiceImpl implements DemoItemService {

    private final DemoItemMapper demoItemMapper;

    @Override
    public PageResult<DemoItemResp> list(int page, int pageSize) {
        Page<DemoItemPo> pageParam = PageHelper.toPage(page, pageSize);
        return PageHelper.toPageResult(
                demoItemMapper.selectPage(pageParam,
                        new LambdaQueryWrapper<DemoItemPo>().orderByDesc(DemoItemPo::getId)),
                DemoItemServiceImpl::toResp
        );
    }

    @Override
    public DemoItemResp getById(Long id) {
        return toResp(findOrThrow(id));
    }

    @Override
    @Transactional
    public DemoItemResp create(CreateDemoItemReq req) {
        DemoItemPo po = new DemoItemPo();
        po.setName(req.getName());
        po.setStatus(req.getStatus());
        demoItemMapper.insert(po);
        return toResp(po);
    }

    @Override
    @Transactional
    public DemoItemResp update(Long id, UpdateDemoItemReq req) {
        DemoItemPo po = findOrThrow(id);
        po.setName(req.getName());
        po.setStatus(req.getStatus());
        demoItemMapper.updateById(po);
        return toResp(po);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findOrThrow(id);
        demoItemMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 私有方法

    private DemoItemPo findOrThrow(Long id) {
        DemoItemPo po = demoItemMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "DemoItem id=" + id + " 不存在");
        }
        return po;
    }

    private static DemoItemResp toResp(DemoItemPo po) {
        DemoItemResp resp = new DemoItemResp();
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setStatus(po.getStatus());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }
}
