package com.example.procurement.integration.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/*
* 将XXL配置接收成Java对象
* 简洁总结一句话：
* @Configuration 声明身份,@EnableConfigurationProperties 绑定配置,@ConditionalOnProperty 控制开关。
* */
// 指定proxyBeanMethods = false 来关闭 CGLIB 代理是。
// 默认值是 true,此时 Spring 会用 CGLIB 动态代理增强这个配置类,保证你在这个类内部多次调用同一个 @Bean 方法时,返回的是同一个单例实例(去重)。
// 设为 false 后,Spring 不做代理,每次调用 @Bean 方法都是普通 Java 方法调用。好处是启动更快、内存占用更小,代价是类内部如果多次调用 @Bean 方法会得到多个不同实例。
// @Configuration 本身告诉 Spring:这是一个配置类,内部通过 @Bean 方法向容器注册组件
@Configuration(proxyBeanMethods = false)
// 把 XxlJobProperties 这个类注册成一个 Spring Bean,并把它和配置文件里的 xxl.job.* 前缀的配置绑定起来。
@EnableConfigurationProperties(XxlJobProperties.class)
// 一个"条件装配"开关。只有当配置里 xxl.job.enabled = true 时,这个配置类才会生效;否则整个类被跳过,不注册任何 Bean。
// prefix = "xxl.job" + name = "enabled" 拼起来就是读取 xxl.job.enabled 这个配置项
// havingValue = "true" 表示:当这个配置项的值等于字符串 "true" 时,条件成立。
// 条件成立 → 整个 XxlJobConfiguration 类(包括里面的 xxlJobExecutor Bean)被加载;
// 条件不成立(比如没配、或配了 false)→ 整个类被忽略,不会创建 XXL-Job 执行器。
@ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "true")
public class XxlJobConfiguration {

    @Bean
    XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        XxlJobProperties.Executor executor = properties.executor();
        if (executor == null || !StringUtils.hasText(executor.appName())) {
            throw new IllegalStateException("xxl.job.executor.app-name must not be blank when XXL-JOB is enabled");
        }
        if (executor.port() < 1 || executor.port() > 65535) {
            throw new IllegalStateException("xxl.job.executor.port must be between 1 and 65535");
        }

        XxlJobSpringExecutor springExecutor = new XxlJobSpringExecutor();
        springExecutor.setAdminAddresses(properties.adminAddresses());
        springExecutor.setAccessToken(properties.accessToken());
        springExecutor.setAppname(executor.appName());
        springExecutor.setAddress(executor.address());
        springExecutor.setIp(executor.ip());
        springExecutor.setPort(executor.port());
        springExecutor.setLogPath(executor.logPath());
        springExecutor.setLogRetentionDays(executor.retentionDays());
        return springExecutor;
    }
}
