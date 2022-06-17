package codingdojo;

import java.util.List;

public class CustomerSync {

    private final CustomerDataLayer customerDataLayer;

    public CustomerSync(CustomerDataLayer customerDataLayer) {
        this.customerDataLayer = customerDataLayer;
    }

    public boolean persist(ExternalCustomer externalCustomer) {
        CustomerMatches customerMatches;
        if (externalCustomer.isCompany()) {
            customerMatches = loadCompany(externalCustomer);
        } else {
            customerMatches = loadPerson(externalCustomer);
        }
        Customer customer = customerMatches.getCustomer();

        if (customer == null) {
            customer = new Customer();
            customer.setExternalId(externalCustomer.getExternalId());
            customer.setMasterExternalId(externalCustomer.getExternalId());
        }

        populateFields(externalCustomer, customer);

        boolean created = false;
        if (customer.getInternalId() == null) {
            customer = createCustomer(customer);
            created = true;
        } else {
            updateCustomer(customer);
        }
        updateContactInfo(externalCustomer, customer);

        if (customerMatches.hasDuplicates()) {
            for (Customer duplicate : customerMatches.getDuplicates()) {
                updateDuplicate(externalCustomer, duplicate);
            }
        }

        updateRelations(externalCustomer, customer);
        updatePreferredStore(externalCustomer, customer);

        return created;
    }

    private void updateRelations(ExternalCustomer externalCustomer, Customer customer) {
        List<ShoppingList> consumerShoppingLists = externalCustomer.getShoppingLists();
        for (ShoppingList consumerShoppingList : consumerShoppingLists) {
            customer.addShoppingList(consumerShoppingList);
            this.customerDataLayer.updateShoppingList(consumerShoppingList);
            this.customerDataLayer.updateCustomerRecord(customer);
        }
    }

    private Customer updateCustomer(Customer customer) {
        return this.customerDataLayer.updateCustomerRecord(customer);
    }

    private void updateDuplicate(ExternalCustomer externalCustomer, Customer duplicate) {
        if (duplicate == null) {
            duplicate = new Customer();
            duplicate.setExternalId(externalCustomer.getExternalId());
            duplicate.setMasterExternalId(externalCustomer.getExternalId());
        }

        duplicate.setName(externalCustomer.getName());

        if (duplicate.getInternalId() == null) {
            createCustomer(duplicate);
        } else {
            updateCustomer(duplicate);
        }
    }

    private void updatePreferredStore(ExternalCustomer externalCustomer, Customer customer) {
        customer.setPreferredStore(externalCustomer.getPreferredStore());
    }

    private Customer createCustomer(Customer customer) {
        return this.customerDataLayer.createCustomerRecord(customer);
    }

    private void populateFields(ExternalCustomer externalCustomer, Customer customer) {
        customer.setName(externalCustomer.getName());
        if (externalCustomer.isCompany()) {
            customer.setCompanyNumber(externalCustomer.getCompanyNumber());
            customer.setCustomerType(CustomerType.COMPANY);
        } else {
            customer.setCustomerType(CustomerType.PERSON);
        }
    }

    private void updateContactInfo(ExternalCustomer externalCustomer, Customer customer) {
        customer.setAddress(externalCustomer.getPostalAddress());
    }

    public CustomerMatches loadCompany(ExternalCustomer externalCustomer) {

        final String externalId = externalCustomer.getExternalId();
        final String companyNumber = externalCustomer.getCompanyNumber();

        CustomerMatches matches = createMatches(externalId, companyNumber);

        if (matches.getCustomer() != null && !CustomerType.COMPANY.equals(matches.getCustomer().getCustomerType())) {
            throw new ConflictException("Existing customer for externalCustomer " + externalId + " already exists and is not a company");
        }

        if ("ExternalId".equals(matches.getMatchTerm())) {
            String customerCompanyNumber = matches.getCustomer().getCompanyNumber();
            if (!companyNumber.equals(customerCompanyNumber)) {
                matches.getCustomer().setMasterExternalId(null);
                matches.addDuplicate(matches.getCustomer());
                matches.setCustomer(null);
                matches.setMatchTerm(null);
            }
        } else if ("CompanyNumber".equals(matches.getMatchTerm())) {
            String customerExternalId = matches.getCustomer().getExternalId();
            if (customerExternalId != null && !externalId.equals(customerExternalId)) {
                throw new ConflictException("Existing customer for externalCustomer " + companyNumber + " doesn't match external id " + externalId + " instead found " + customerExternalId);
            }
            Customer customer = matches.getCustomer();
            customer.setExternalId(externalId);
            customer.setMasterExternalId(externalId);
            matches.addDuplicate(null);
        }

        return matches;
    }

    private CustomerMatches createMatches(String externalId, String companyNumber) {
        CustomerMatches matches;
        Customer matchByExternalId = customerDataLayer.findByExternalId(externalId);
        if (matchByExternalId != null) {
            matches = new CustomerMatches(matchByExternalId);
            matches.setMatchTerm("ExternalId");
            Customer matchByMasterId = customerDataLayer.findByMasterExternalId(externalId);
            if (matchByMasterId != null) matches.addDuplicate(matchByMasterId);
            return matches;
        } else {
            Customer matchByCompanyNumber = customerDataLayer.findByCompanyNumber(companyNumber);
            if (matchByCompanyNumber != null) {
                matches = new CustomerMatches(matchByCompanyNumber);
                matches.setMatchTerm("CompanyNumber");
            } else {
                matches = new CustomerMatches();
            }
        }
        return matches;
    }

    public CustomerMatches loadPerson(ExternalCustomer externalCustomer) {
        final String externalId = externalCustomer.getExternalId();

        CustomerMatches matches = new CustomerMatches();
        Customer matchByPersonalNumber = customerDataLayer.findByExternalId(externalId);
        matches.setCustomer(matchByPersonalNumber);
        if (matchByPersonalNumber != null) matches.setMatchTerm("ExternalId");

        if (matches.getCustomer() != null) {
            if (!CustomerType.PERSON.equals(matches.getCustomer().getCustomerType())) {
                throw new ConflictException("Existing customer for externalCustomer " + externalId + " already exists and is not a person");
            }

            if (!"ExternalId".equals(matches.getMatchTerm())) {
                Customer customer = matches.getCustomer();
                customer.setExternalId(externalId);
                customer.setMasterExternalId(externalId);
            }
        }

        return matches;
    }
}
