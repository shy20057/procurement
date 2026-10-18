package com.example.procurement.integration.support;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.BeanUtils;
/** 显式区分 DO 和响应 DTO，字段同名时只复制值，不承担数据库访问。 */
public final class ResponseAssembler {
    private ResponseAssembler() {}
    public static <T> T copy(Object source, Supplier<T> factory) {
        if (source == null) return null;
        T target = factory.get();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    /*
    * 	批量把 List<DO> 转成 List<DTO>(内部调 copy)
    * */
    public static <S,T> List<T> list(List<S> source, Supplier<T> factory) {
        return source.stream().map(row -> copy(row, factory)).toList();
    }
}
