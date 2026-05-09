package com.hify.demo.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.demo.domain.DemoItemService;
import com.hify.demo.web.req.CreateDemoItemReq;
import com.hify.demo.web.req.UpdateDemoItemReq;
import com.hify.demo.web.resp.DemoItemResp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/demo-items")
@RequiredArgsConstructor
public class DemoItemController {

    private final DemoItemService demoItemService;

    @GetMapping
    public Result<PageResult<DemoItemResp>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(demoItemService.list(page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<DemoItemResp> getById(@PathVariable Long id) {
        return Result.ok(demoItemService.getById(id));
    }

    @PostMapping
    public Result<DemoItemResp> create(@Valid @RequestBody CreateDemoItemReq req) {
        return Result.ok(demoItemService.create(req));
    }

    @PutMapping("/{id}")
    public Result<DemoItemResp> update(@PathVariable Long id,
                                       @Valid @RequestBody UpdateDemoItemReq req) {
        return Result.ok(demoItemService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        demoItemService.delete(id);
        return Result.ok();
    }
}
