package com.orders.order_management.core.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.orders.order_management..repository..*(..))")
    public void enableTenantFilter() {
        String outletId = SecurityUtils.getCurrentOutletId();
        Session session = entityManager.unwrap(Session.class);
        if (outletId != null) {
            org.hibernate.Filter filter = session.enableFilter("outletFilter");
            filter.setParameter("outletId", outletId);
        } else {
            session.disableFilter("outletFilter");
        }
    }
}
