import { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQuery, useMutation } from '@tanstack/react-query';
import { Button, Card, CardContent, CardHeader, CardTitle } from '@supporthub/ui';
import { useAuthStore } from '../store/authStore.js';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const MAX_FILES = 5;
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
const MAX_SUBJECT_LEN = 200;
const MAX_DESCRIPTION_LEN = 2000;

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface Category {
  id: string;
  name: string;
  subcategories: Subcategory[];
}

interface Subcategory {
  id: string;
  name: string;
}

interface PresignResponse {
  uploadUrl: string;
  attachmentId: string;
}

type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

interface FileUploadState {
  file: File;
  progress: number;
  attachmentId: string | null;
  error: string | null;
  uploading: boolean;
  done: boolean;
}

// ---------------------------------------------------------------------------
// Zod schema
// ---------------------------------------------------------------------------

const createTicketSchema = z.object({
  title: z
    .string()
    .min(10, 'Subject must be at least 10 characters')
    .max(MAX_SUBJECT_LEN, `Subject must be at most ${MAX_SUBJECT_LEN} characters`),
  description: z
    .string()
    .min(20, 'Description must be at least 20 characters')
    .max(MAX_DESCRIPTION_LEN, `Description must be at most ${MAX_DESCRIPTION_LEN} characters`),
  categoryId: z.string().uuid('Please select a category').min(1, 'Category is required'),
  subCategoryId: z.string().optional(),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'URGENT']),
  orderReference: z.string().optional(),
});

type CreateTicketFormValues = z.infer<typeof createTicketSchema>;

// ---------------------------------------------------------------------------
// API helpers
// ---------------------------------------------------------------------------

async function fetchCategories(token: string, tenantId: string): Promise<Category[]> {
  const res = await fetch('/api/v1/categories', {
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Tenant-ID': tenantId,
    },
  });
  if (!res.ok) throw new Error(`Failed to load categories: ${res.status}`);
  return res.json() as Promise<Category[]>;
}

async function presignAttachment(
  token: string,
  tenantId: string,
  fileName: string,
  contentType: string,
): Promise<PresignResponse> {
  const res = await fetch('/api/v1/attachments/presign', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Tenant-ID': tenantId,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ fileName, contentType }),
  });
  if (!res.ok) throw new Error(`Failed to get presigned URL: ${res.status}`);
  return res.json() as Promise<PresignResponse>;
}

function uploadFileXhr(
  uploadUrl: string,
  file: File,
  onProgress: (pct: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    });
    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        onProgress(100);
        resolve();
      } else {
        reject(new Error(`Upload failed: ${xhr.status}`));
      }
    });
    xhr.addEventListener('error', () => { reject(new Error('Upload network error')); });
    xhr.open('PUT', uploadUrl);
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream');
    xhr.send(file);
  });
}

async function submitTicket(
  token: string,
  tenantId: string,
  values: CreateTicketFormValues,
  attachmentIds: string[],
): Promise<void> {
  const res = await fetch('/api/v1/tickets', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Tenant-ID': tenantId,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      title: values.title,
      description: values.description,
      categoryId: values.categoryId,
      subCategoryId: values.subCategoryId ?? undefined,
      priority: values.priority,
      orderReference: values.orderReference ?? undefined,
      attachmentIds,
      channel: 'WEB',
    }),
  });
  if (!res.ok) throw new Error(`Failed to create ticket: ${res.status}`);
}

// ---------------------------------------------------------------------------
// Subcomponents
// ---------------------------------------------------------------------------

function FileRow({
  state,
  onRemove,
}: {
  state: FileUploadState;
  onRemove: () => void;
}) {
  const sizeMb = (state.file.size / (1024 * 1024)).toFixed(2);
  return (
    <div className="flex items-center gap-3 rounded-md border border-gray-200 bg-gray-50 px-3 py-2">
      <div className="min-w-0 flex-1">
        <p className="truncate text-xs font-medium text-gray-800">{state.file.name}</p>
        <p className="text-xs text-gray-500">{sizeMb} MB</p>
        {state.uploading && (
          <div className="mt-1 h-1.5 w-full rounded-full bg-gray-200">
            <div
              className="h-1.5 rounded-full bg-blue-500 transition-all"
              style={{ width: `${state.progress}%` }}
            />
          </div>
        )}
        {state.error != null && (
          <p className="mt-0.5 text-xs text-red-600">{state.error}</p>
        )}
        {state.done && (
          <p className="mt-0.5 text-xs text-green-600">Uploaded</p>
        )}
      </div>
      {!state.uploading && (
        <button
          type="button"
          onClick={onRemove}
          className="shrink-0 text-gray-400 hover:text-red-500"
          aria-label="Remove file"
        >
          ×
        </button>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main component
// ---------------------------------------------------------------------------

export function CreateTicketPage() {
  const navigate = useNavigate();
  const { token, tenantId } = useAuthStore();
  const safeToken = token ?? '';
  const safeTenantId = tenantId ?? '';

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [fileStates, setFileStates] = useState<FileUploadState[]>([]);
  const [fileError, setFileError] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    control,
    formState: { errors, isSubmitting },
  } = useForm<CreateTicketFormValues>({
    resolver: zodResolver(createTicketSchema),
    defaultValues: {
      priority: 'MEDIUM',
      title: '',
      description: '',
      categoryId: '',
      subCategoryId: '',
      orderReference: '',
    },
  });

  const titleValue = watch('title');
  const descriptionValue = watch('description');
  const selectedCategoryId = watch('categoryId');

  // ---- Categories query ----
  const { data: categories = [] } = useQuery<Category[]>({
    queryKey: ['categories', safeTenantId],
    queryFn: () => fetchCategories(safeToken, safeTenantId),
    enabled: safeToken.length > 0 && safeTenantId.length > 0,
    staleTime: 10 * 60_000,
  });

  const selectedCategory = categories.find((c) => c.id === selectedCategoryId);
  const subcategories: Subcategory[] = selectedCategory?.subcategories ?? [];

  // ---- Submit mutation ----
  const submitMutation = useMutation({
    mutationFn: async (values: CreateTicketFormValues) => {
      // 1. Upload any pending files
      setIsUploading(true);
      const uploadedIds: string[] = [];

      await Promise.all(
        fileStates.map(async (fs, idx) => {
          if (fs.done && fs.attachmentId != null) {
            uploadedIds.push(fs.attachmentId);
            return;
          }
          try {
            setFileStates((prev) =>
              prev.map((s, i) => (i === idx ? { ...s, uploading: true, error: null } : s)),
            );
            const { uploadUrl, attachmentId } = await presignAttachment(
              safeToken,
              safeTenantId,
              fs.file.name,
              fs.file.type || 'application/octet-stream',
            );
            await uploadFileXhr(uploadUrl, fs.file, (pct) => {
              setFileStates((prev) =>
                prev.map((s, i) => (i === idx ? { ...s, progress: pct } : s)),
              );
            });
            setFileStates((prev) =>
              prev.map((s, i) =>
                i === idx ? { ...s, uploading: false, done: true, attachmentId } : s,
              ),
            );
            uploadedIds.push(attachmentId);
          } catch (err) {
            const msg = err instanceof Error ? err.message : 'Upload failed';
            setFileStates((prev) =>
              prev.map((s, i) =>
                i === idx ? { ...s, uploading: false, error: msg } : s,
              ),
            );
            throw err;
          }
        }),
      );

      setIsUploading(false);

      // 2. Submit ticket
      await submitTicket(safeToken, safeTenantId, values, uploadedIds);
    },
    onSuccess: () => {
      void navigate('/tickets');
    },
  });

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFileError(null);
    const incoming = Array.from(e.target.files ?? []);

    const oversized = incoming.filter((f) => f.size > MAX_FILE_SIZE_BYTES);
    if (oversized.length > 0) {
      setFileError(`Files must be under 10 MB. Oversized: ${oversized.map((f) => f.name).join(', ')}`);
      e.target.value = '';
      return;
    }

    if (fileStates.length + incoming.length > MAX_FILES) {
      setFileError(`You can attach at most ${MAX_FILES} files.`);
      e.target.value = '';
      return;
    }

    const newStates: FileUploadState[] = incoming.map((f) => ({
      file: f,
      progress: 0,
      attachmentId: null,
      error: null,
      uploading: false,
      done: false,
    }));
    setFileStates((prev) => [...prev, ...newStates]);
    e.target.value = '';
  };

  const removeFile = (idx: number) => {
    setFileStates((prev) => prev.filter((_, i) => i !== idx));
  };

  const onSubmit = (values: CreateTicketFormValues) => {
    submitMutation.mutate(values);
  };

  return (
    <Card className="mx-auto max-w-2xl">
      <CardHeader>
        <CardTitle>Create Support Ticket</CardTitle>
      </CardHeader>
      <CardContent>
        <form
          onSubmit={(e) => {
            void handleSubmit(onSubmit)(e);
          }}
          className="flex flex-col gap-5"
          noValidate
        >
          {/* Category */}
          <div className="flex flex-col gap-1">
            <label htmlFor="categoryId" className="text-sm font-medium text-gray-700">
              Category <span className="text-red-500">*</span>
            </label>
            <Controller
              name="categoryId"
              control={control}
              render={({ field }) => (
                <select
                  id="categoryId"
                  {...field}
                  className="rounded-md border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600"
                >
                  <option value="">Select a category...</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              )}
            />
            {errors.categoryId != null && (
              <p className="text-xs text-red-600" role="alert">{errors.categoryId.message}</p>
            )}
          </div>

          {/* Subcategory (only shown when a category is selected and has subcategories) */}
          {subcategories.length > 0 && (
            <div className="flex flex-col gap-1">
              <label htmlFor="subCategoryId" className="text-sm font-medium text-gray-700">
                Subcategory
              </label>
              <Controller
                name="subCategoryId"
                control={control}
                render={({ field }) => (
                  <select
                    id="subCategoryId"
                    {...field}
                    className="rounded-md border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600"
                  >
                    <option value="">Select a subcategory (optional)...</option>
                    {subcategories.map((sc) => (
                      <option key={sc.id} value={sc.id}>
                        {sc.name}
                      </option>
                    ))}
                  </select>
                )}
              />
            </div>
          )}

          {/* Priority */}
          <div className="flex flex-col gap-2">
            <span className="text-sm font-medium text-gray-700">
              Priority <span className="text-red-500">*</span>
            </span>
            <div className="flex gap-4">
              {(['LOW', 'MEDIUM', 'HIGH', 'URGENT'] as Priority[]).map((p) => (
                <label key={p} className="flex cursor-pointer items-center gap-1.5 text-sm">
                  <input
                    type="radio"
                    value={p}
                    {...register('priority')}
                    className="text-blue-600"
                  />
                  {p.charAt(0) + p.slice(1).toLowerCase()}
                </label>
              ))}
            </div>
            {errors.priority != null && (
              <p className="text-xs text-red-600" role="alert">{errors.priority.message}</p>
            )}
          </div>

          {/* Subject */}
          <div className="flex flex-col gap-1">
            <div className="flex items-center justify-between">
              <label htmlFor="title" className="text-sm font-medium text-gray-700">
                Subject <span className="text-red-500">*</span>
              </label>
              <span className="text-xs text-gray-400">
                {(titleValue ?? '').length} / {MAX_SUBJECT_LEN}
              </span>
            </div>
            <input
              id="title"
              type="text"
              maxLength={MAX_SUBJECT_LEN}
              placeholder="Briefly describe your issue"
              className="rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600"
              aria-invalid={errors.title != null}
              {...register('title')}
            />
            {errors.title != null && (
              <p className="text-xs text-red-600" role="alert">{errors.title.message}</p>
            )}
          </div>

          {/* Description */}
          <div className="flex flex-col gap-1">
            <div className="flex items-center justify-between">
              <label htmlFor="description" className="text-sm font-medium text-gray-700">
                Description <span className="text-red-500">*</span>
              </label>
              <span className="text-xs text-gray-400">
                {(descriptionValue ?? '').length} / {MAX_DESCRIPTION_LEN}
              </span>
            </div>
            <textarea
              id="description"
              rows={5}
              maxLength={MAX_DESCRIPTION_LEN}
              placeholder="Describe your issue in detail..."
              className="rounded-md border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600"
              aria-invalid={errors.description != null}
              {...register('description')}
            />
            {errors.description != null && (
              <p className="text-xs text-red-600" role="alert">{errors.description.message}</p>
            )}
          </div>

          {/* Order Reference */}
          <div className="flex flex-col gap-1">
            <label htmlFor="orderReference" className="text-sm font-medium text-gray-700">
              Order Reference <span className="text-xs text-gray-400">(optional)</span>
            </label>
            <input
              id="orderReference"
              type="text"
              placeholder="e.g. ORD-123456"
              className="rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600"
              {...register('orderReference')}
            />
          </div>

          {/* File attachments */}
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-gray-700">
                Attachments <span className="text-xs text-gray-400">(optional, max {MAX_FILES} files, 10 MB each)</span>
              </span>
              <button
                type="button"
                disabled={fileStates.length >= MAX_FILES}
                onClick={() => { fileInputRef.current?.click(); }}
                className="rounded-md border border-gray-300 bg-white px-3 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
              >
                Add files
              </button>
            </div>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              accept="*/*"
              className="hidden"
              onChange={handleFileChange}
            />
            {fileError != null && (
              <p className="text-xs text-red-600" role="alert">{fileError}</p>
            )}
            {fileStates.length > 0 && (
              <div className="flex flex-col gap-2">
                {fileStates.map((fs, idx) => (
                  <FileRow
                    key={`${fs.file.name}-${fs.file.size}-${idx}`}
                    state={fs}
                    onRemove={() => { removeFile(idx); }}
                  />
                ))}
              </div>
            )}
          </div>

          {/* Error from submit */}
          {submitMutation.isError && (
            <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
              {submitMutation.error instanceof Error
                ? submitMutation.error.message
                : 'Submission failed. Please try again.'}
            </div>
          )}

          {/* Actions */}
          <div className="flex gap-3 pt-2">
            <Button
              type="submit"
              isLoading={isSubmitting || submitMutation.isPending || isUploading}
              className="flex-1"
            >
              Submit Ticket
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                void navigate('/');
              }}
            >
              Cancel
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
