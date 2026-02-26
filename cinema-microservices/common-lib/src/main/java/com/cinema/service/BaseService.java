package com.cinema.service;

import com.cinema.dto.request.BasePageRequest;
import com.cinema.dto.request.PageableUtils;
import com.cinema.dto.response.BasePageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
public abstract class BaseService<E, ID, D> {

    /**
     * Trả về repository được sử dụng cho CRUD operations
     */
    protected abstract JpaRepository<E, ID> getRepository();

    /**
     * Trả về specification executor được sử dụng cho filter operations
     */
    protected abstract JpaSpecificationExecutor<E> getSpecificationExecutor();

    /**
     * Trả về function convert Entity sang DTO
     */
    protected abstract Function<E, D> toDtoConverter();

    // ============= PAGINATION METHODS =============

    /**
     * Phân trang không filter
     * 
     * @param request request chứa page, size, sort info
     * @return page result
     */
    public BasePageResponse<D> getPage(BasePageRequest request) {
        request.validate();
        Pageable pageable = PageableUtils.toPageable(request);
        Page<E> page = getRepository().findAll(pageable);
        return convertToPageResponse(page);
    }

    /**
     * Phân trang không filter với default sort fields
     * 
     * @param request           request chứa page, size, sort info
     * @param defaultSortFields tên các fields dùng để sort mặc định
     * @return page result
     */
    public BasePageResponse<D> getPage(BasePageRequest request, String... defaultSortFields) {
        request.validate();
        Pageable pageable = PageableUtils.toPageable(request, defaultSortFields);
        Page<E> page = getRepository().findAll(pageable);
        return convertToPageResponse(page);
    }

    /**
     * Phân trang có filter (dùng Specification)
     * 
     * @param request       request chứa page, size, sort info
     * @param specification Specification<E> dùng để filter
     * @return page result
     */
    public BasePageResponse<D> getPage(BasePageRequest request, Specification<E> specification) {
        request.validate();
        Pageable pageable = PageableUtils.toPageable(request);
        Page<E> page = getSpecificationExecutor().findAll(specification, pageable);
        return convertToPageResponse(page);
    }

    /**
     * Phân trang có filter với default sort fields
     * 
     * @param request           request chứa page, size, sort info
     * @param specification     Specification<E> dùng để filter
     * @param defaultSortFields tên các fields dùng để sort mặc định
     * @return page result
     */
    public BasePageResponse<D> getPage(BasePageRequest request, Specification<E> specification,
            String... defaultSortFields) {
        request.validate();
        Pageable pageable = PageableUtils.toPageable(request, defaultSortFields);
        Page<E> page = getSpecificationExecutor().findAll(specification, pageable);
        return convertToPageResponse(page);
    }

    /**
     * Chuyển đổi Spring Data Page sang BasePageResponse
     */
    protected BasePageResponse<D> convertToPageResponse(Page<E> page) {
        List<D> dtos = page.getContent().stream()
                .map(toDtoConverter())
                .collect(Collectors.toList());

        return new BasePageResponse<>(dtos, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    // ============= CRUD METHODS =============

    /**
     * Lấy tất cả entities
     * 
     * @return danh sách tất cả DTO
     */
    public List<D> getAll() {
        return getRepository().findAll().stream()
                .map(toDtoConverter())
                .collect(Collectors.toList());
    }

    /**
     * Tìm entity theo ID
     * 
     * @param id ID của entity
     * @return Optional chứa DTO nếu tìm thấy
     */
    public Optional<D> findById(ID id) {
        return getRepository().findById(id).map(toDtoConverter());
    }

    /**
     * Kiểm tra entity có tồn tại theo ID
     * 
     * @param id ID của entity
     * @return true nếu tồn tại, false nếu không
     */
    public boolean existsById(ID id) {
        return getRepository().existsById(id);
    }

    /**
     * Lấy tổng số entities
     * 
     * @return số lượng entities
     */
    public long count() {
        return getRepository().count();
    }

    /**
     * Lấy các entities với filter
     * 
     * @param specification Specification<E> dùng để filter
     * @return danh sách DTO
     */
    public List<D> findAll(Specification<E> specification) {
        return getSpecificationExecutor().findAll(specification).stream()
                .map(toDtoConverter())
                .collect(Collectors.toList());
    }

    /**
     * Lưu entity
     * 
     * @param entity entity cần lưu
     * @return DTO của entity sau khi lưu
     */
    public D save(E entity) {
        E saved = getRepository().save(entity);
        return toDtoConverter().apply(saved);
    }

    /**
     * Xóa entity theo ID
     * 
     * @param id ID của entity cần xóa
     */
    public void deleteById(ID id) {
        getRepository().deleteById(id);
    }

    /**
     * Lấy tổng số entities với filter
     * 
     * @param specification Specification<E> dùng để filter
     * @return số lượng entities thỏa mãn filter
     */
    public long count(Specification<E> specification) {
        return getSpecificationExecutor().count(specification);
    }
}
