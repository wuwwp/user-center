package com.wwp.usercenter.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 集成测试共享容器、Redis 清理和认证 HTTP 辅助方法；子类复用同一个 Spring 上下文。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestSupport {

  /** 容器在 JVM 内只启动一次，两个集成测试类共享；Ryuk 在 JVM 退出时清理。 */
  static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();

  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.4")).withDatabaseName("usercenter");

  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  static {
    if (DOCKER_AVAILABLE) {
      MYSQL.start();
      REDIS.start();
    }
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    if (!DOCKER_AVAILABLE) {
      return;
    }
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> "");
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected StringRedisTemplate redisTemplate;

  @BeforeEach
  void flushRedis() {
    redisTemplate.execute(
        (RedisCallback<Void>)
            connection -> {
              connection.serverCommands().flushDb();
              return null;
            });
  }

  protected ResultActions attemptLogin(String email, String password) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email":"%s","password":"%s"}
                """
                    .formatted(email, password)));
  }

  protected String login(String email, String password) throws Exception {
    MvcResult result = attemptLogin(email, password).andExpect(status().isOk()).andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
  }

  protected void expectLogin(String email, String password, int code) throws Exception {
    attemptLogin(email, password)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(code));
  }
}
