package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.config.PolicySourceProperties;
import com.itheima.policydailyagent.dto.PolicySourceOption;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/policy-sources")
public class PolicySourceController {

    private final PolicySourceProperties policySourceProperties;

    public PolicySourceController(PolicySourceProperties policySourceProperties) {
        this.policySourceProperties = policySourceProperties;
    }

    @GetMapping
    public ResponseEntity<List<PolicySourceOption>> listSources() {
        return ResponseEntity.ok(policySourceProperties.getItems().stream()
                .map(item -> new PolicySourceOption(
                        item.getId(),
                        item.getName(),
                        item.getLevel(),
                        item.getRegion(),
                        item.getType(),
                        item.getUrl(),
                        item.isDirectCrawl(),
                        item.getKeywords()
                ))
                .toList());
    }
}
