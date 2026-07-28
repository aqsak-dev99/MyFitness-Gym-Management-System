package com.gymmanagement.model;

public class Staff extends Person {

    private String  staffId;
    private String  role;
    private boolean available;

    public Staff(String personId, String staffId, String name,
                 String email, String phone, String role) {
        super(personId, name, email, phone);
        if (staffId == null || staffId.isBlank())
            throw new IllegalArgumentException("Staff ID cannot be empty.");
        this.staffId   = staffId;
        this.role      = role;
        this.available = true;
    }

    public String  getStaffId()              { return staffId;   }
    public String  getRole()                 { return role;      }
    public boolean isAvailable()             { return available; }
    public void    setRole(String role)      { this.role      = role;      }
    public void    setAvailable(boolean av)  { this.available = av;        }

    @Override
    public String getDetails() {
        return "--- Staff ---\n"
             + "Staff ID  : " + staffId    + "\n"
             + "Name      : " + getName()  + "\n"
             + "Email     : " + getEmail() + "\n"
             + "Phone     : " + getPhone() + "\n"
             + "Role      : " + role       + "\n"
             + "Available : " + available;
    }
}
