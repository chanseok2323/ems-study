package com.chanseok.emsstudy.utility;

import com.chanseok.emsstudy.provider.ApplicationContextProvider;
import org.springframework.context.ApplicationContext;

public class BeanUtils {

    public static Object getBean(String beanName) {
        return getContext().getBean(beanName);
    }

    public static Object getBean(Class<?> clazz) {
        return getContext().getBean(clazz);
    }

    private static ApplicationContext getContext() {
        return ApplicationContextProvider.getApplicationContext();
    }
}
