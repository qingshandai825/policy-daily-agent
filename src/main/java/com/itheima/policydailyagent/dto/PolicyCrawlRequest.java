package com.itheima.policydailyagent.dto;

import jakarta.validation.constraints.NotBlank;

public record PolicyCrawlRequest(

        @NotBlank(message = "政策网页链接不能为空")
        String url

) {
}
