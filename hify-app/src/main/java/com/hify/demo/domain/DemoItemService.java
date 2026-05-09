package com.hify.demo.domain;

import com.hify.common.web.PageResult;
import com.hify.demo.web.req.CreateDemoItemReq;
import com.hify.demo.web.req.UpdateDemoItemReq;
import com.hify.demo.web.resp.DemoItemResp;

public interface DemoItemService {

    PageResult<DemoItemResp> list(int page, int pageSize);

    DemoItemResp getById(Long id);

    DemoItemResp create(CreateDemoItemReq req);

    DemoItemResp update(Long id, UpdateDemoItemReq req);

    void delete(Long id);
}
