package com.enterprise.order.customer.specification;

import com.enterprise.order.customer.entity.Customer;
import org.springframework.data.jpa.domain.Specification;

public class CustomerSpecification {

    public static Specification<Customer> emailContains(String email) {
        return (root, query, criteriaBuilder) ->
                email == null || email.isBlank() ? null :
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), "%" + email.toLowerCase() + "%");
    }

    public static Specification<Customer> firstNameContains(String firstName) {
        return (root, query, criteriaBuilder) ->
                firstName == null || firstName.isBlank() ? null :
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), "%" + firstName.toLowerCase() + "%");
    }

    public static Specification<Customer> lastNameContains(String lastName) {
        return (root, query, criteriaBuilder) ->
                lastName == null || lastName.isBlank() ? null :
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), "%" + lastName.toLowerCase() + "%");
    }

    public static Specification<Customer> statusEquals(String status) {
        return (root, query, criteriaBuilder) ->
                status == null || status.isBlank() ? null :
                        criteriaBuilder.equal(root.get("status"), Customer.CustomerStatus.valueOf(status.toUpperCase()));
    }

    public static Specification<Customer> cityContains(String city) {
        return (root, query, criteriaBuilder) ->
                city == null || city.isBlank() ? null :
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("city")), "%" + city.toLowerCase() + "%");
    }

    public static Specification<Customer> countryContains(String country) {
        return (root, query, criteriaBuilder) ->
                country == null || country.isBlank() ? null :
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("country")), "%" + country.toLowerCase() + "%");
    }
}

