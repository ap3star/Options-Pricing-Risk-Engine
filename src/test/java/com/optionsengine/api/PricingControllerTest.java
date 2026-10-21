package com.optionsengine.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PricingControllerTest {

    private static final String OPTION = """
            {"spot":100,"strike":100,"rate":0.05,"dividendYield":0,"volatility":0.2,"expiry":1,"type":"CALL"}""";

    @Autowired
    MockMvc mvc;

    /** Jackson/JsonPath hands back BigDecimal for decimals, which Hamcrest's closeTo(double) rejects. */
    private static org.hamcrest.Matcher<Object> near(double expected, double tolerance) {
        return new org.hamcrest.TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(Object actual) {
                return actual instanceof Number n && Math.abs(n.doubleValue() - expected) <= tolerance;
            }

            @Override
            public void describeTo(org.hamcrest.Description d) {
                d.appendText("within " + tolerance + " of " + expected);
            }
        };
    }

    private org.springframework.test.web.servlet.ResultActions call(String url, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void pricesWithBlackScholesByDefault() throws Exception {
        call("/api/v1/price", "{\"option\":" + OPTION + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("BLACK_SCHOLES"))
                .andExpect(jsonPath("$.price").value(near(10.4506, 1e-4)))
                .andExpect(jsonPath("$.stdError").doesNotExist());
    }

    @Test
    void pricesWithMonteCarlo() throws Exception {
        call("/api/v1/price", "{\"option\":" + OPTION
                + ",\"model\":\"MONTE_CARLO\",\"monteCarlo\":{\"paths\":1000000,\"seed\":1}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("MONTE_CARLO"))
                .andExpect(jsonPath("$.price").value(near(10.4506, 0.1)))
                .andExpect(jsonPath("$.stdError").exists())
                .andExpect(jsonPath("$.paths").value(1000000));
    }

    @Test
    void returnsGreeks() throws Exception {
        call("/api/v1/greeks", "{\"option\":" + OPTION + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.greeks.delta").value(near(0.6368, 1e-4)))
                .andExpect(jsonPath("$.greeks.gamma").value(near(0.01876, 1e-4)))
                .andExpect(jsonPath("$.greeks.vega").value(near(37.524, 1e-2)));
    }

    @Test
    void solvesImpliedVol() throws Exception {
        call("/api/v1/implied-vol", """
                {"marketPrice":10.450583572185565,"spot":100,"strike":100,"rate":0.05,
                 "dividendYield":0,"expiry":1,"type":"CALL"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impliedVolatility").value(near(0.2, 1e-8)));
    }

    @Test
    void impliedVolRejectsArbitrageViolatingPrice() throws Exception {
        call("/api/v1/implied-vol", """
                {"marketPrice":150,"spot":100,"strike":100,"rate":0.05,
                 "dividendYield":0,"expiry":1,"type":"CALL"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("upper bound")));
    }

    @Test
    void computesVar() throws Exception {
        call("/api/v1/var", """
                {"confidence":0.9,
                 "positions":[{"symbol":"AAA","kind":"STOCK","quantity":10,"spot":100}],
                 "logReturns":{"AAA":[-0.05,-0.02,0.0,0.01,0.02,0.03,-0.01,0.04,-0.03,0.05]}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarios").value(10))
                .andExpect(jsonPath("$.portfolioValue").value(near(1000.0, 1e-9)))
                .andExpect(jsonPath("$.valueAtRisk").value(near(1000 - 1000 * Math.exp(-0.03), 1e-9)));
    }

    @Test
    void varSupportsOptionPositions() throws Exception {
        call("/api/v1/var", """
                {"confidence":0.8,
                 "positions":[{"symbol":"AAA","kind":"OPTION","quantity":5,"spot":100,"strike":100,
                               "rate":0.03,"volatility":0.3,"expiry":0.5,"type":"PUT"}],
                 "logReturns":{"AAA":[-0.05,-0.02,0.0,0.01,0.02,0.03,-0.01,0.04,-0.03,0.05]}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valueAtRisk").value(org.hamcrest.Matchers.greaterThan(0.0)));
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        call("/api/v1/price", "{\"option\":{\"spot\":-1,\"strike\":100,\"volatility\":0.2,\"expiry\":1,\"type\":\"CALL\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("spot")));
        call("/api/v1/price", "not json").andExpect(status().isBadRequest());
        call("/api/v1/var", """
                {"confidence":0.9,
                 "positions":[{"symbol":"AAA","kind":"OPTION","quantity":1,"spot":100}],
                 "logReturns":{"AAA":[0.01]}}""")
                .andExpect(status().isBadRequest());
    }
}
