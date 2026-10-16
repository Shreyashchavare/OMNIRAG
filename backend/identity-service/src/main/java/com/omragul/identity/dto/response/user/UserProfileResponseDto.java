package com.omragul.identity.dto.response.user;

import com.omragul.identity.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponseDto {

    private String firstName;

    private String lastName;

    private String phoneNumber;

    private Gender gender;

    private LocalDate dateOfBirth;

    private String timezone;

    private String language;

    private String bio;

    private String profileImageUrl;
}