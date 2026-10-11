package com.youthletter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthletter.global.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 노션 A25 프로필 수정(multipart)·사진 DB 저장, A8 프로필 확장 필드. */
@SpringBootTest(properties = "crawler.cron=-") @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class ProfileImageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;
    @Autowired jakarta.persistence.EntityManager em;
    Long userId;
    String bearer, key;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("youthletter_test");
        key = "prof-" + UUID.randomUUID();
        userId = jdbc.queryForObject("INSERT INTO users(email,name,nickname) VALUES (?,'t',?) RETURNING id",
                Long.class, key + "@test.invalid", key.substring(key.length() - 20));
        bearer = "Bearer " + tokens.createAccessToken(userId, key + "@test.invalid", "USER");
    }

    MockMultipartHttpServletRequestBuilder patchProfile() {
        MockMultipartHttpServletRequestBuilder b = multipart(HttpMethod.PATCH, "/api/me/profile");
        b.header("Authorization", bearer);
        return b;
    }
    static MockMultipartFile file(byte[] bytes, String type) { return new MockMultipartFile("image", "photo", type, bytes); }

    static byte[] jpeg(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED); g.fillRect(0, 0, w / 2, h); g.setColor(Color.BLUE); g.fillRect(w / 2, 0, w - w / 2, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", out);
        return out.toByteArray();
    }
    /** SOI 뒤에 Orientation 태그만 있는 Exif APP1 세그먼트를 삽입한다(빅엔디안). */
    static byte[] withOrientation(byte[] jpeg, int orientation) {
        byte[] tiff = {'M','M',0,42, 0,0,0,8, 0,1, 0x01,0x12, 0,3, 0,0,0,1, 0,(byte) orientation,0,0, 0,0,0,0};
        byte[] exif = new byte[6 + tiff.length];
        System.arraycopy("Exif\0\0".getBytes(StandardCharsets.US_ASCII), 0, exif, 0, 6);
        System.arraycopy(tiff, 0, exif, 6, tiff.length);
        int len = exif.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF); out.write(0xD8); out.write(0xFF); out.write(0xE1); out.write(len >> 8); out.write(len & 0xFF);
        out.writeBytes(exif);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }
    static byte[] heic(int size) {
        byte[] b = new byte[size];
        byte[] head = {0,0,0,24,'f','t','y','p','h','e','i','c'};
        System.arraycopy(head, 0, b, 0, head.length);
        return b;
    }
    JsonNode data(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("data");
    }
    String imagePath(JsonNode profile) { return profile.get("profileImageUrl").asText().replaceFirst("^https?://[^/]+", ""); }

    @Test void largeRotatedJpegIsShrunkUprightAndServedPublicly() throws Exception {
        byte[] upload = withOrientation(jpeg(2000, 1000), 6); // 가로 사진 + "90도 회전해서 보여라"
        JsonNode profile = data(mvc.perform(patchProfile().file(file(upload, "image/jpeg")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileImageUrl").isNotEmpty()));
        em.flush();
        var row = jdbc.queryForMap("SELECT content_type, byte_size, width, height FROM profile_images WHERE user_id=?", userId);
        assertThat(row.get("content_type")).isEqualTo("image/jpeg");
        assertThat((Integer) row.get("width")).isEqualTo(256);  // 회전 적용 → 세로 사진
        assertThat((Integer) row.get("height")).isEqualTo(512);
        assertThat((Integer) row.get("byte_size")).isLessThan(upload.length).isLessThan(100_000);

        byte[] served = mvc.perform(get(imagePath(profile))) // 인증 없이 조회
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(served));
        assertThat(decoded.getWidth()).isEqualTo(256);
        assertThat(decoded.getHeight()).isEqualTo(512);
        assertThat(new String(served, StandardCharsets.ISO_8859_1)).doesNotContain("Exif"); // 메타데이터 제거
        // 원본 왼쪽(빨강)이 90° 시계방향 회전 후 위쪽에 와야 한다
        Color top = new Color(decoded.getRGB(128, 20));
        assertThat(top.getRed()).isGreaterThan(200);
        assertThat(top.getBlue()).isLessThan(60);
    }

    @Test void transparentPngBecomesJpegAndReuploadRotatesKey() throws Exception {
        BufferedImage png = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(png, "png", out);
        JsonNode first = data(mvc.perform(patchProfile().file(file(out.toByteArray(), "image/png"))).andExpect(status().isOk()));
        em.flush();
        assertThat(jdbc.queryForObject("SELECT content_type FROM profile_images WHERE user_id=?", String.class, userId)).isEqualTo("image/jpeg");
        JsonNode second = data(mvc.perform(patchProfile().file(file(jpeg(64, 64), "image/jpeg"))).andExpect(status().isOk()));
        em.flush();
        assertThat(imagePath(second)).isNotEqualTo(imagePath(first));
        mvc.perform(get(imagePath(first))).andExpect(status().isNotFound());
        mvc.perform(get(imagePath(second))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM profile_images WHERE user_id=?", Integer.class, userId)).isEqualTo(1);
    }

    @Test void heicIsStoredAsIsWithinOneMegabyte() throws Exception {
        JsonNode profile = data(mvc.perform(patchProfile().file(file(heic(2048), "application/octet-stream"))).andExpect(status().isOk()));
        em.flush();
        mvc.perform(get(imagePath(profile))).andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/heic"));
        mvc.perform(patchProfile().file(file(heic(1024 * 1024 + 1), "image/heic")))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.status").value(4106));
    }

    @Test void rejectsUnsupportedCorruptAndConflictingInput() throws Exception {
        mvc.perform(patchProfile().file(file("GIF89a....".getBytes(), "image/jpeg"))) // 확장자/타입 위장
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(4107));
        byte[] corrupt = Arrays.copyOf(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, 64);
        mvc.perform(patchProfile().file(file(corrupt, "image/jpeg")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(4108));
        mvc.perform(patchProfile().file(file(jpeg(10, 10), "image/jpeg")).param("removeImage", "true"))
                .andExpect(status().isBadRequest());
        mvc.perform(patchProfile().param("avatarId", "../etc")).andExpect(status().isBadRequest());
        mvc.perform(patchProfile().param("bio", "가".repeat(101))).andExpect(status().isBadRequest());
        mvc.perform(patchProfile().param("nickname", "a")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/me/profile").header("Authorization", bearer).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(HttpMethod.PATCH, "/api/me/profile").param("bio", "x")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM profile_images WHERE user_id=?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT bio FROM users WHERE id=?", String.class, userId)).isNull();
    }

    @Test void textFieldsArePartialAndProfileExposesAppFields() throws Exception {
        jdbc.update("INSERT INTO oauth_accounts(user_id,provider,provider_id) VALUES (?,'KAKAO',?)", userId, key);
        mvc.perform(patchProfile().param("bio", "  매일 경제 공부  ").param("avatarId", "bee_2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("매일 경제 공부"))
                .andExpect(jsonPath("$.data.avatarId").value("bee_2"))
                .andExpect(jsonPath("$.data.nickname").value(key.substring(key.length() - 20)))
                .andExpect(jsonPath("$.data.oauthProvider").value("kakao"))
                .andExpect(jsonPath("$.data.joinedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.profileImageUrl").doesNotExist());
        mvc.perform(patchProfile().param("nickname", "새닉네임" + key.substring(key.length() - 6)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bio").value("매일 경제 공부"));
        mvc.perform(patchProfile().param("bio", "")).andExpect(status().isOk()).andExpect(jsonPath("$.data.bio").doesNotExist());

        mvc.perform(patchProfile().file(file(jpeg(32, 32), "image/jpeg"))).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileImageUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.avatarId").value("bee_2"));
        mvc.perform(patchProfile().param("removeImage", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileImageUrl").doesNotExist());
        em.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM profile_images WHERE user_id=?", Integer.class, userId)).isZero();
    }

    @Test void duplicateNicknameAndUnknownImage() throws Exception {
        String taken = "taken" + key.substring(key.length() - 10);
        jdbc.update("INSERT INTO users(email,name,nickname) VALUES (?,'t',?)", key + "x@test.invalid", taken);
        mvc.perform(patchProfile().param("nickname", taken)).andExpect(status().isConflict());
        mvc.perform(get("/api/profile-images/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/profile-images/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.oauthProvider").doesNotExist()); // 이메일 가입 계정
    }
}
