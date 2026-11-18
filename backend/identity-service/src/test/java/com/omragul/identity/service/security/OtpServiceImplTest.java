package com.omragul.identity.service.security;

import com.omragul.identity.entity.auth.Otp;
import com.omragul.identity.entity.user.User;
import com.omragul.identity.enums.OtpPurpose;
import com.omragul.identity.repository.OtpRepository;
import com.omragul.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock
    private OtpRepository otpRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private OtpServiceImpl otpService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpService, "otpExpirationMinutes", 5L);
        userId = UUID.randomUUID();
        user = User.builder().userId(userId).username("testuser").build();
    }

    @Test
    @DisplayName("generateOtp: generates 6-digit hashed OTP without logging plaintext to System.out")
    void shouldGenerateOtpWithoutLoggingPlaintext() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        try {
            when(userRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
            when(otpRepository.findByUserUserIdAndPurposeAndVerifiedFalse(userId, OtpPurpose.PASSWORD_RESET))
                    .thenReturn(Collections.emptyList());
            when(passwordEncoder.encode(anyString())).thenReturn("hashed_otp");

            otpService.generateOtp(userId, OtpPurpose.PASSWORD_RESET);

            ArgumentCaptor<Otp> captor = ArgumentCaptor.forClass(Otp.class);
            verify(otpRepository).save(captor.capture());

            Otp savedOtp = captor.getValue();
            assertThat(savedOtp.getOtpCodeHash()).isEqualTo("hashed_otp");
            assertThat(savedOtp.getVerified()).isFalse();
            assertThat(savedOtp.getExpiresAt()).isNotNull();

            // Verify stdout contains NO OTP log
            String output = outContent.toString();
            assertThat(output).doesNotContain("Generated OTP");
        } finally {
            System.setOut(originalOut);
        }
    }
}
