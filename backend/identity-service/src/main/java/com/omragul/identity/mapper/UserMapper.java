package com.omragul.identity.mapper;

import com.omragul.identity.dto.request.admin.AdminUpdateUserRequestDto;
import com.omragul.identity.dto.request.user.UpdateUserRequestDto;
import com.omragul.identity.dto.response.admin.UserDetailsResponseDto;
import com.omragul.identity.dto.response.admin.UserSummaryResponseDto;
import com.omragul.identity.dto.response.user.UserResponseDto;
import com.omragul.identity.entity.user.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        uses = UserProfileMapper.class
)
public interface UserMapper {

    @Mapping(source = "userProfile", target = "profile")
    UserResponseDto toResponseDto(User user);

    UserSummaryResponseDto toSummaryResponseDto(User user);

    @Mapping(source = "userProfile", target = "profile")
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    UserDetailsResponseDto toDetailsResponseDto(User user);

    @BeanMapping(
            nullValuePropertyMappingStrategy =
                    NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    @Mapping(target = "accountLocked", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)

    @Mapping(target = "userProfile", ignore = true)
    @Mapping(target = "userRoles", ignore = true)
    @Mapping(target = "userPermissions", ignore = true)
    @Mapping(target = "refreshTokens", ignore = true)
    @Mapping(target = "passwordHistory", ignore = true)
    @Mapping(target = "otps", ignore = true)
    @Mapping(target = "userSessions", ignore = true)
    @Mapping(target = "loginHistory", ignore = true)

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateUser(
            UpdateUserRequestDto dto,
            @MappingTarget User user
    );

    @BeanMapping(
            nullValuePropertyMappingStrategy =
                    NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "accountLocked", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)

    @Mapping(target = "userProfile", ignore = true)
    @Mapping(target = "userRoles", ignore = true)
    @Mapping(target = "userPermissions", ignore = true)
    @Mapping(target = "refreshTokens", ignore = true)
    @Mapping(target = "passwordHistory", ignore = true)
    @Mapping(target = "otps", ignore = true)
    @Mapping(target = "userSessions", ignore = true)
    @Mapping(target = "loginHistory", ignore = true)

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateAdminUser(
            AdminUpdateUserRequestDto dto,
            @MappingTarget User user
    );
}



//------- did to avoid warnings--------//
//@Mapper(
//        componentModel = "spring",
//        uses = UserProfileMapper.class
//)
//public interface UserMapper {
//
//    UserResponseDto toResponseDto(User user);
//
//    UserSummaryResponseDto toSummaryResponseDto(User user);
//
//    UserDetailsResponseDto toDetailsResponseDto(User user);
//
//    void updateUser(
//            UpdateUserRequestDto dto,
//            @MappingTarget User user
//    );
//
//    void updateAdminUser(
//            AdminUpdateUserRequestDto dto,
//            @MappingTarget User user
//    );
//}