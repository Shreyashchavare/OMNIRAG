package com.omragul.identity.mapper;

import com.omragul.identity.dto.request.rbac.CreateRoleRequestDto;
import com.omragul.identity.dto.request.rbac.UpdateRoleRequestDto;
import com.omragul.identity.dto.response.rbac.RoleResponseDto;
import com.omragul.identity.entity.rbac.Role;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring"
)
public interface RoleMapper {

    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "userRoles", ignore = true)
    @Mapping(target = "rolePermissions", ignore = true)
    Role toEntity(CreateRoleRequestDto dto);

    @Mapping(target = "permissions", ignore = true)
    RoleResponseDto toResponseDto(Role role);

    @BeanMapping(
            nullValuePropertyMappingStrategy =
                    NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "userRoles", ignore = true)
    @Mapping(target = "rolePermissions", ignore = true)

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateRole(
            UpdateRoleRequestDto dto,
            @MappingTarget Role role
    );
}

//
//@Mapper(
//        componentModel = "spring",
//        uses = PermissionMapper.class
//)
//public interface RoleMapper {
//
//    Role toEntity(CreateRoleRequestDto dto);
//
//    RoleResponseDto toResponseDto(Role role);
//
//    void updateRole(
//            UpdateRoleRequestDto dto,
//            @MappingTarget Role role
//    );
//}