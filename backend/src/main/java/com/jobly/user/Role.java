package com.jobly.user;

/**
 * What an account may do. Visitors have no account, so they have no role.
 * وش يقدر يسوي الحساب. الزائر ما عنده حساب، فما عنده دور.
 */
public enum Role {
    // Looks for jobs and applies. / يدوّر على وظائف ويقدّم.
    CANDIDATE,
    // Posts jobs for a company and reviews applicants. / ينشر وظائف لشركة ويراجع المتقدمين.
    EMPLOYER,
    // Manages accounts. / يدير الحسابات.
    ADMIN
}
