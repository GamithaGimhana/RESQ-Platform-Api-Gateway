package com.resq.gateway;

import com.resq.gateway.config.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "eureka.client.register-with-eureka=false",
    "eureka.client.fetch-registry=false"
})
class ApiGatewayApplicationTests {

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void contextLoads() {
        assertNotNull(jwtUtil);
    }

    @Test
    void testJwtTokenGenerationAndValidation() {
        String username = "dispatcher@resq.gov";
        String role = "DISPATCHER";
        String fullName = "Incident Dispatcher";

        String token = jwtUtil.generateToken(username, role, fullName);
        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));

        assertEquals(username, jwtUtil.extractUsername(token));
        assertEquals(com.resq.gateway.model.Role.DISPATCHER, jwtUtil.extractRole(token));
    }
}
