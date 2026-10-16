package com.omragul.identity.dto.request.user;

import com.omragul.identity.enums.Gender;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequestDto {

    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phoneNumber;

    private Gender gender;

    private LocalDate dateOfBirth;

    @Size(max = 50, message = "Timezone must not exceed 50 characters")
    private String timezone;

    @Size(max = 20, message = "Language must not exceed 20 characters")
    private String language;

    @Size(max = 1000, message = "Bio must not exceed 1000 characters")
    private String bio;

    @Size(max = 500, message = "Profile image URL must not exceed 500 characters")
    private String profileImageUrl;
}