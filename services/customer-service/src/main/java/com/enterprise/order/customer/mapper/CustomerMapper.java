package com.enterprise.order.customer.mapper;

import com.enterprise.order.customer.dto.CustomerDTO;
import com.enterprise.order.customer.entity.Customer;
import com.enterprise.order.shared.dto.AddressDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CustomerMapper {

    // The entity stores the address as flat columns; rebuild the nested AddressDTO on read.
    @Mapping(source = "city", target = "address.city")
    @Mapping(source = "state", target = "address.state")
    @Mapping(source = "zipCode", target = "address.zipCode")
    @Mapping(source = "country", target = "address.country")
    CustomerDTO toDTO(Customer customer);

    // Flatten the nested AddressDTO into the entity's address columns on write.
    @Mapping(source = "address.city", target = "city")
    @Mapping(source = "address.state", target = "state")
    @Mapping(source = "address.zipCode", target = "zipCode")
    @Mapping(source = "address.country", target = "country")
    Customer toEntity(CustomerDTO customerDTO);

    @Named("customerDTOToCustomer")
    default Customer customerDTOToCustomer(CustomerDTO dto) {
        if (dto == null) {
            return null;
        }

        Customer customer = Customer.builder()
                .id(dto.getId())
                .email(dto.getEmail())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .phone(dto.getPhone())
                .build();

        if (dto.getAddress() != null) {
            customer.setCity(dto.getAddress().getCity());
            customer.setState(dto.getAddress().getState());
            customer.setZipCode(dto.getAddress().getZipCode());
            customer.setCountry(dto.getAddress().getCountry());
        }

        return customer;
    }

    @Named("customerToCustomerDTO")
    default CustomerDTO customerToCustomerDTO(Customer customer) {
        if (customer == null) {
            return null;
        }

        AddressDTO address = null;
        if (customer.getCity() != null || customer.getState() != null ||
            customer.getZipCode() != null || customer.getCountry() != null) {
            address = AddressDTO.builder()
                    .city(customer.getCity())
                    .state(customer.getState())
                    .zipCode(customer.getZipCode())
                    .country(customer.getCountry())
                    .build();
        }

        return CustomerDTO.builder()
                .id(customer.getId())
                .email(customer.getEmail())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .phone(customer.getPhone())
                .address(address)
                .status(customer.getStatus() != null ? customer.getStatus().name() : null)
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
}

