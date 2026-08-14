package com.omragul.identity.mapper;

import com.omragul.identity.dto.request.rbac.CreatePermissionRequestDto;
import com.omragul.identity.dto.request.rbac.UpdatePermissionRequestDto;
import com.omragul.identity.dto.response.rbac.PermissionResponseDto;
import com.omragul.identity.entity.rbac.Permission;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

    @Mapping(target = "permissionId", ignore = true)
    @Mapping(target = "rolePermissions", ignore = true)
    @Mapping(target = "userPermissions", ignore = true)
    Permission toEntity(CreatePermissionRequestDto dto);

    PermissionResponseDto toResponseDto(Permission permission);

    @BeanMapping(
            nullValuePropertyMappingStrategy =
                    NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "permissionId", ignore = true)
    @Mapping(target = "rolePermissions", ignore = true)
    @Mapping(target = "userPermissions", ignore = true)

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updatePermission(
            UpdatePermissionRequestDto dto,
            @MappingTarget Permission permission
    );
}

//
//@Mapper(componentModel = "spring")
//public interface PermissionMapper {
//
//    Permission toEntity(CreatePermissionRequestDto dto);
//
//    PermissionResponseDto toResponseDto(Permission permission);
//
//    void updatePermission(
//            UpdatePermissionRequestDto dto,
//            @MappingTarget Permission permission
//    );
//}