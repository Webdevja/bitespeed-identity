package com.bitespeed.identity.service;

import com.bitespeed.identity.dto.ContactResponse;
import com.bitespeed.identity.entity.Contact;
import com.bitespeed.identity.repository.ContactRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class IdentityService {

    private final ContactRepository contactRepository;

    public IdentityService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    public ContactResponse identify(String email, String phoneNumber) {

        List<Contact> matchedContacts =
                contactRepository.findByEmailOrPhoneNumber(email, phoneNumber);

        Contact primaryContact;

        // CASE 1: No contact exists
        if (matchedContacts.isEmpty()) {

            Contact newContact = new Contact();
            newContact.setEmail(email);
            newContact.setPhoneNumber(phoneNumber);
            newContact.setLinkPrecedence("primary");

            primaryContact = contactRepository.save(newContact);
        }

        // CASE 2: Contacts exist
        else {

            // Find all primary contacts involved
            Set<Contact> primaries = new HashSet<>();

            for (Contact c : matchedContacts) {
                if ("primary".equals(c.getLinkPrecedence())) {
                    primaries.add(c);
                } else {
                    Contact parent =
                            contactRepository.findById(c.getLinkedId()).orElse(null);
                    if (parent != null) primaries.add(parent);
                }
            }

            // Choose oldest primary (smallest id)
            primaryContact = primaries.stream()
                    .min(Comparator.comparing(Contact::getId))
                    .get();

            // Merge other primaries
            for (Contact p : primaries) {

                if (!p.getId().equals(primaryContact.getId())) {

                    p.setLinkedId(primaryContact.getId());
                    p.setLinkPrecedence("secondary");
                    contactRepository.save(p);

                    List<Contact> children =
                            contactRepository.findByLinkedIdOrId(p.getId(), p.getId());

                    for (Contact child : children) {

                        if (!child.getId().equals(primaryContact.getId())) {
                            child.setLinkedId(primaryContact.getId());
                            child.setLinkPrecedence("secondary");
                            contactRepository.save(child);
                        }
                    }
                }
            }

            boolean exactMatch = matchedContacts.stream().anyMatch(
                    c -> Objects.equals(c.getEmail(), email)
                            && Objects.equals(c.getPhoneNumber(), phoneNumber)
            );

            if (!exactMatch) {

                Contact secondary = new Contact();
                secondary.setEmail(email);
                secondary.setPhoneNumber(phoneNumber);
                secondary.setLinkedId(primaryContact.getId());
                secondary.setLinkPrecedence("secondary");

                contactRepository.save(secondary);
            }
        }

        // Fetch full cluster
        List<Contact> allContacts =
                contactRepository.findByLinkedIdOrId(primaryContact.getId(),
                                                     primaryContact.getId());

        List<String> emails = allContacts.stream()
                .map(Contact::getEmail)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<String> phoneNumbers = allContacts.stream()
                .map(Contact::getPhoneNumber)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<Long> secondaryIds = allContacts.stream()
                .filter(c -> "secondary".equals(c.getLinkPrecedence()))
                .map(Contact::getId)
                .collect(Collectors.toList());

        ContactResponse response = new ContactResponse();

        response.setPrimaryContactId(primaryContact.getId());
        response.setEmails(emails);
        response.setPhoneNumbers(phoneNumbers);
        response.setSecondaryContactIds(secondaryIds);

        return response;
    }
}