package com.example.lending.user;

/** users の 1 行。 */
public record UserRow(long id, String displayName, String email, Role role) {

}
