package com.converter.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonUtilTest {

    @Test
    void escapesBackslashAndDoubleQuote() {
        assertThat(JsonUtil.jsonString("a\\b\"c")).isEqualTo("\"a\\\\b\\\"c\"");
    }

    @Test
    void escapesNewlineCarriageReturnAndTab() {
        assertThat(JsonUtil.jsonString("line1\nline2\r\tend")).isEqualTo("\"line1\\nline2\\r\\tend\"");
    }

    @Test
    void escapesOtherControlCharactersAsUnicode() {
        String withControlChar = "a" + (char) 1 + "b";
        assertThat(JsonUtil.jsonString(withControlChar)).isEqualTo("\"a\\u0001b\"");
    }

    @Test
    void leavesOrdinaryTextUnchanged() {
        assertThat(JsonUtil.jsonString("Preuve illisible, merci de renvoyer.")).isEqualTo(
                "\"Preuve illisible, merci de renvoyer.\"");
    }

    /** Le vrai test de non-regression : le fragment produit doit rester un JSON valide. */
    @Test
    void producesValidJsonAcceptedByAStandardParser() throws Exception {
        String reason = "Motif sur\nplusieurs lignes avec un \"guillemet\" et un \\backslash.";
        String fragment = "{\"reason\":" + JsonUtil.jsonString(reason) + "}";

        JsonNode node = new ObjectMapper().readTree(fragment);

        assertThat(node.get("reason").asText()).isEqualTo(reason);
    }
}
