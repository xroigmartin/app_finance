import { Component, EventEmitter, Input, Output, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';

/** Reusable pager for a backend-paginated list: page size selector + prev/next + range label. */
@Component({
  selector: 'app-pagination',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagination">
      <span class="muted range">{{ rangeLabel }}</span>
      <label class="page-size">
        Por página
        <select [ngModel]="size" (ngModelChange)="changeSize($event)">
          @for (option of pageSizeOptions; track option) {
            <option [ngValue]="option">{{ option }}</option>
          }
        </select>
      </label>
      <button class="btn small" type="button" [disabled]="!hasPrevious" (click)="previous()">‹ Anterior</button>
      <span class="muted page-indicator">Página {{ page + 1 }} de {{ totalPages || 1 }}</span>
      <button class="btn small" type="button" [disabled]="!hasNext" (click)="next()">Siguiente ›</button>
    </div>
  `,
  styles: `
    .pagination {
      display: flex;
      align-items: center;
      gap: 1rem;
      flex-wrap: wrap;
      padding-top: 0.75rem;
    }
    .range { margin-right: auto; }
  `
})
export class Pagination {
  /** 0-based current page. */
  @Input({ required: true }) page = 0;
  @Input({ required: true }) size = 25;
  @Input({ required: true }) totalElements = 0;
  @Input() pageSizeOptions = [5, 10, 25, 50, 100];

  @Output() pageChange = new EventEmitter<number>();
  @Output() sizeChange = new EventEmitter<number>();

  get totalPages(): number {
    return this.size <= 0 ? 0 : Math.ceil(this.totalElements / this.size);
  }

  get hasPrevious(): boolean {
    return this.page > 0;
  }

  get hasNext(): boolean {
    return this.page + 1 < this.totalPages;
  }

  get rangeLabel(): string {
    if (this.totalElements === 0) {
      return '0 de 0';
    }
    const from = this.page * this.size + 1;
    const to = Math.min(from + this.size - 1, this.totalElements);
    return `${from}–${to} de ${this.totalElements}`;
  }

  previous(): void {
    if (this.hasPrevious) {
      this.pageChange.emit(this.page - 1);
    }
  }

  next(): void {
    if (this.hasNext) {
      this.pageChange.emit(this.page + 1);
    }
  }

  /** Changing the page size resets to the first page (the current page number would stop matching). */
  changeSize(newSize: number): void {
    this.pageChange.emit(0);
    this.sizeChange.emit(newSize);
  }
}
