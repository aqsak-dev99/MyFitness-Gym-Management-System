package com.gymmanagement.model;

public abstract class Person {

    private String personId;
    private String name;
    private String email;
    private String phone;

    public Person(String personId, String name, String email, String phone) {
        if (personId == null || personId.isBlank())
            throw new IllegalArgumentException("Person ID cannot be empty.");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Name cannot be empty.");
        this.personId = personId;
        this.name     = name;
        this.email    = (email != null) ? email : "";
        this.phone    = (phone != null) ? phone : "";
    }

    public String getPersonId() { return personId; }
    public String getName()     { return name;     }
    public String getEmail()    { return email;    }
    public String getPhone()    { return phone;    }

    public void setName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Name cannot be blank.");
        this.name = name;
    }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }

    public abstract String getDetails();

    @Override
    public String toString() { return getDetails(); }
}
