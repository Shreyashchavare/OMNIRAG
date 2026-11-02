package com.omragul.identity.dto.request.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignupProfileRequestDto {

    @JsonAlias({"first_name", "firstName"})
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @JsonAlias({"last_name", "lastName"})
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @JsonAlias({"phone_number", "phoneNumber"})
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phoneNumber;
}