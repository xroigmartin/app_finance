import { TestBed } from '@angular/core/testing';
import { Pagination } from './pagination';

describe('Pagination', () => {
  function create(page: number, size: number, totalElements: number): Pagination {
    const p = TestBed.createComponent(Pagination).componentInstance;
    p.page = page;
    p.size = size;
    p.totalElements = totalElements;
    return p;
  }

  it('calcula totalPages redondeando hacia arriba', () => {
    expect(create(0, 25, 240).totalPages).toBe(10);
    expect(create(0, 25, 251).totalPages).toBe(11);
  });

  it('totalPages es 0 sin elementos', () => {
    expect(create(0, 25, 0).totalPages).toBe(0);
  });

  it('hasPrevious/hasNext según la página actual', () => {
    const first = create(0, 25, 100);
    expect(first.hasPrevious).toBe(false);
    expect(first.hasNext).toBe(true);

    const last = create(3, 25, 100);
    expect(last.hasPrevious).toBe(true);
    expect(last.hasNext).toBe(false);
  });

  it('rangeLabel describe el tramo mostrado', () => {
    expect(create(0, 25, 240).rangeLabel).toBe('1–25 de 240');
    expect(create(9, 25, 240).rangeLabel).toBe('226–240 de 240');
  });

  it('rangeLabel sin elementos', () => {
    expect(create(0, 25, 0).rangeLabel).toBe('0 de 0');
  });

  it('previous emite page-1 solo si hasPrevious', () => {
    const p = create(2, 25, 100);
    const onPage = vi.fn();
    p.pageChange.subscribe(onPage);
    p.previous();
    expect(onPage).toHaveBeenCalledWith(1);

    const first = create(0, 25, 100);
    const onPageFirst = vi.fn();
    first.pageChange.subscribe(onPageFirst);
    first.previous();
    expect(onPageFirst).not.toHaveBeenCalled();
  });

  it('next emite page+1 solo si hasNext', () => {
    const p = create(0, 25, 100);
    const onPage = vi.fn();
    p.pageChange.subscribe(onPage);
    p.next();
    expect(onPage).toHaveBeenCalledWith(1);

    const last = create(3, 25, 100);
    const onPageLast = vi.fn();
    last.pageChange.subscribe(onPageLast);
    last.next();
    expect(onPageLast).not.toHaveBeenCalled();
  });

  it('changeSize emite sizeChange con el nuevo tamaño y resetea a la página 0', () => {
    const p = create(3, 25, 240);
    const onSize = vi.fn();
    const onPage = vi.fn();
    p.sizeChange.subscribe(onSize);
    p.pageChange.subscribe(onPage);
    p.changeSize(50);
    expect(onPage).toHaveBeenCalledWith(0);
    expect(onSize).toHaveBeenCalledWith(50);
  });

  it('expone las opciones de tamaño por defecto 5/10/25/50/100', () => {
    expect(create(0, 25, 0).pageSizeOptions).toEqual([5, 10, 25, 50, 100]);
  });
});
