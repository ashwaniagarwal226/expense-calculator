package com.expense.calculator.service;

import com.expense.calculator.category.CategoryService;
import com.expense.calculator.domain.Profile;
import com.expense.calculator.dto.ProfileDto;
import com.expense.calculator.repository.ProfileRepository;
import com.expense.calculator.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * People whose expenses are tracked. There is always at least one profile; the first one is the
 * "default" that legacy data and the old /v1/hdfc endpoints use.
 */
@Service
public class ProfileService {

    static final String DEFAULT_NAME = "Me";
    static final String DEFAULT_EMOJI = "🙂";

    private final ProfileRepository profiles;
    private final TransactionRepository transactions;
    private final CategoryService categories;

    public ProfileService(ProfileRepository profiles, TransactionRepository transactions, CategoryService categories) {
        this.profiles = profiles;
        this.transactions = transactions;
        this.categories = categories;
    }

    @Transactional
    public Profile defaultProfile() {
        return profiles.findAll().stream().min(Comparator.comparing(Profile::getId))
                .orElseGet(() -> profiles.save(Profile.builder().name(DEFAULT_NAME).emoji(DEFAULT_EMOJI)
                        .createDt(LocalDateTime.now()).build()));
    }

    @Transactional
    public List<ProfileDto> list() {
        defaultProfile();
        return profiles.findAll().stream().sorted(Comparator.comparing(Profile::getId))
                .map(p -> ProfileDto.of(p, transactions.countByProfileId(p.getId()))).toList();
    }

    public Profile get(Long id) {
        return profiles.findById(id).orElseThrow(() -> new NoSuchElementException("Profile " + id + " not found"));
    }

    /** Resolves an optional profile id from a request: null stays null ("everyone"), unknown ids are rejected. */
    public Long checkOptional(Long id) {
        if (id != null) get(id);
        return id;
    }

    /** Uploads must belong to someone: null means the default profile. */
    public Long resolveForUpload(Long id) {
        return id == null ? defaultProfile().getId() : get(id).getId();
    }

    @Transactional
    public ProfileDto create(String name, String emoji) {
        String clean = validName(name);
        if (profiles.existsByNameIgnoreCase(clean)) {
            throw new IllegalArgumentException("A profile called " + clean + " already exists");
        }
        Profile p = profiles.save(Profile.builder().name(clean).emoji(blankToDefault(emoji))
                .createDt(LocalDateTime.now()).build());
        return ProfileDto.of(p, 0);
    }

    @Transactional
    public ProfileDto update(Long id, String name, String emoji) {
        Profile p = get(id);
        String clean = validName(name);
        if (!clean.equalsIgnoreCase(p.getName()) && profiles.existsByNameIgnoreCase(clean)) {
            throw new IllegalArgumentException("A profile called " + clean + " already exists");
        }
        p.setName(clean);
        p.setEmoji(blankToDefault(emoji));
        return ProfileDto.of(profiles.save(p), transactions.countByProfileId(id));
    }

    /** Deletes the profile together with its transactions and its own rules. */
    @Transactional
    public int delete(Long id) {
        get(id);
        if (profiles.count() <= 1) {
            throw new IllegalArgumentException("You need at least one profile");
        }
        int removed = transactions.deleteByProfileId(id);
        categories.deleteRulesOf(id);
        profiles.deleteById(id);
        return removed;
    }

    private static String validName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required");
        String clean = name.trim();
        if (clean.length() > 60) throw new IllegalArgumentException("Name is too long");
        return clean;
    }

    private static String blankToDefault(String emoji) {
        return emoji == null || emoji.isBlank() ? DEFAULT_EMOJI : emoji.trim();
    }
}
