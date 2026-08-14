package com.omragul.identity.mapper;

import com.omragul.identity.dto.request.user.SignupProfileRequestDto;
import com.omragul.identity.dto.request.user.UpdateUserProfileRequestDto;
import com.omragul.identity.dto.response.user.UserProfileResponseDto;
import com.omragul.identity.entity.user.UserProfile;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {

    @Mapping(target = "profileId", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "departmentId", ignore = true)
    @Mapping(target = "gender", ignore = true)
    @Mapping(target = "profileImageUrl", ignore = true)
    @Mapping(target = "dateOfBirth", ignore = true)
    @Mapping(target = "timezone", ignore = true)
    @Mapping(target = "language", ignore = true)
    @Mapping(target = "bio", ignore = true)
    UserProfile toEntity(SignupProfileRequestDto dto);

    UserProfileResponseDto toResponseDto(UserProfile userProfile);

    @BeanMapping(
            nullValuePropertyMappingStrategy =
                    NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "profileId", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "departmentId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateProfile(
            UpdateUserProfileRequestDto dto,
            @MappingTarget UserProfile userProfile
    );
}



//
//@Mapper(componentModel = "spring")
//public interface UserProfileMapper {
//
//    UserProfile toEntity(SignupProfileRequestDto dto);
//
//    UserProfileResponseDto toResponseDto(UserProfile userProfile);
//
//    void updateProfile(
//            UpdateUserProfileRequestDto dto,
//            @MappingTarget UserProfile userProfile
//    );
//}