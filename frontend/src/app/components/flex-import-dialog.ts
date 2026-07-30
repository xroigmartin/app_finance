
import { Component, EventEmitter, Input, Output, inject, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { ApiService } from '../api.service';
import { FlexImportResult } from '../models';

/**
 * Diálogo de import del Flex Query de IBKR (adaptación del patrón de
 * import-dialog.ts): sube el XML a la cartera indicada y muestra el resumen
 * ok/duplicadas/errores/warnings (RF-4, RN-4).
 */
@Component({
  selector: 'app-flex-import-dialog',
  imports: [],
  template: `
    <button class="btn" (click)="open()">{{ label }}</button>

    @if (visible) {
      <div class="overlay" (click)="close()">
        <div class="dialog card" (click)="$event.stopPropagation()">
          <h2>Importar Flex Query (IBKR)</h2>

          <p class="muted hint">
            Acepta el <strong>Activity Flex Query</strong> anual de Interactive Brokers en formato
            <code>XML</code>. La divisa base de la cuenta debe coincidir con la de la cartera;
            las operaciones ya importadas se omiten como duplicadas (reimportar es seguro).
          </p>

          <label class="field">Informe a importar
            <div class="file-zone" (click)="fileInput.click()">
              @if (file) {
                <strong>{{ file.name }}</strong>
              } @else {
                <span class="muted">Haz clic para elegir el fichero .xml del Flex Query</span>
              }
            </div>
          </label>
          <input #fileInput type="file" accept=".xml" hidden (change)="onFile(fileInput)">

          @if (error) {
            <p class="amount-expense">{{ error }}</p>
          }
          @if (result) {
            <div class="result">
              <p>
                <strong>{{ result.imported }}</strong> operaciones importadas.
                @if (result.duplicated > 0) {
                  <span class="muted">{{ result.duplicated }} omitidas por estar ya registradas.</span>
                }
                @if (result.errors.length > 0) {
                  <span class="amount-expense">{{ result.errors.length }} filas con errores.</span>
                }
              </p>
              @if (result.errors.length > 0) {
                <ul class="errors">
                  @for (e of result.errors; track $index) {
                    <li>{{ e.section }}@if (e.reference) {<span> ({{ e.reference }})</span>}: {{ e.message }}</li>
                  }
                </ul>
              }
              @if (result.warnings.length > 0) {
                <p class="warn-title">Avisos ({{ result.warnings.length }}):</p>
                <ul class="warnings">
                  @for (w of result.warnings; track $index) {
                    <li>{{ w }}</li>
                  }
                </ul>
              }
              @if (result.errors.length > 0 || result.warnings.length > 0) {
                <button class="btn small" (click)="goToImportHistory()">Ver detalle en Importaciones →</button>
              }
            </div>
          }

          <div class="dialog-actions">
            <button class="btn primary" (click)="doImport()" [disabled]="loading || !file">
              {{ loading ? 'Importando…' : 'Importar' }}
            </button>
            <button class="btn" (click)="close()">{{ result ? 'Cerrar' : 'Cancelar' }}</button>
          </div>
        </div>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: `
    .overlay {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, .45);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 100;
    }

    .dialog {
      width: min(620px, 92vw);
      max-height: 85vh;
      overflow-y: auto;

      h2 {
        font-size: 1.1rem;
        margin: 0 0 .75rem;
        color: var(--text);
      }
    }

    .hint {
      font-size: .85rem;
      margin: 0 0 1rem;

      code {
        background: var(--bg);
        border: 1px solid var(--border);
        padding: .1rem .3rem;
        border-radius: 2px;
      }
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: .35rem;
      font-size: .85rem;
      font-weight: 600;
      color: var(--text-muted);
      margin-bottom: 1rem;
    }

    .file-zone {
      border: 1px dashed var(--border-strong);
      border-radius: 2px;
      padding: 1.25rem;
      text-align: center;
      cursor: pointer;
      font-weight: 400;

      &:hover {
        border-color: var(--accent);
        background: var(--bg);
      }
    }

    .result p {
      margin: 0 0 .5rem;
    }

    .errors {
      margin: 0 0 .75rem;
      padding-left: 1.2rem;
      max-height: 160px;
      overflow-y: auto;
      font-size: .85rem;
      color: var(--neg);
    }

    .warn-title {
      font-weight: 600;
      color: var(--accent);
    }

    .warnings {
      margin: 0 0 .75rem;
      padding-left: 1.2rem;
      max-height: 160px;
      overflow-y: auto;
      font-size: .85rem;
      color: var(--accent);
    }

    .dialog-actions {
      display: flex;
      gap: .5rem;
      justify-content: flex-end;
      margin-top: .5rem;
    }
  `
})
export class FlexImportDialog {
  private api = inject(ApiService);
  private router = inject(Router);

  @Input({ required: true }) portfolioId!: number;
  @Input() label = 'Importar Flex';
  @Output() done = new EventEmitter<void>();

  visible = false;
  loading = false;
  file: File | null = null;
  result: FlexImportResult | null = null;
  error = '';

  open(): void {
    this.visible = true;
    this.file = null;
    this.result = null;
    this.error = '';
  }

  close(): void {
    this.visible = false;
  }

  onFile(input: HTMLInputElement): void {
    this.file = input.files?.[0] ?? null;
    input.value = '';
    this.result = null;
    this.error = '';
  }

  doImport(): void {
    if (!this.file) return;
    this.loading = true;
    this.result = null;
    this.error = '';
    this.api.importFlexReport(this.portfolioId, this.file).subscribe({
      next: result => {
        this.loading = false;
        this.result = result;
        this.file = null;
        this.done.emit();
      },
      error: (e: HttpErrorResponse) => {
        this.loading = false;
        this.error = e.error?.detail ?? e.error?.message ?? 'Error al importar el informe.';
      }
    });
  }

  /** Cierra el diálogo y lleva al detalle persistido (RF-12) por si el usuario quiere revisarlo. */
  goToImportHistory(): void {
    this.close();
    this.router.navigate(['/investments/operations'], { queryParams: { tab: 'importaciones' } });
  }
}
