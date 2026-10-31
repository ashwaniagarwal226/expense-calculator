package com.expense.calculator.config;

import com.expense.calculator.category.CategoryService;
import com.expense.calculator.migration.LegacyDataMigrator;
import com.expense.calculator.service.ProfileService;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Prepares the database before the web server accepts requests (an ApplicationRunner would run after
 * Tomcat is already serving, so an early request could see half-migrated data or create a second
 * default profile): default profile -> legacy data migration -> category/rule seeding.
 */
@Component
public class StartupInitializer implements SmartInitializingSingleton {

    private final ProfileService profiles;
    private final LegacyDataMigrator migrator;
    private final CategoryService categories;

    public StartupInitializer(ProfileService profiles, LegacyDataMigrator migrator, CategoryService categories) {
        this.profiles = profiles;
        this.migrator = migrator;
        this.categories = categories;
    }

    @Override
    public void afterSingletonsInstantiated() {
        profiles.defaultProfile();
        migrator.migrate();
        categories.seed();
    }
}
