import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AxiosError } from 'axios'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import {
  createCategory,
  deleteCategory,
  listCategories,
  updateCategory,
  type CategoryInput,
} from '@/services/productApi'
import type { BaseResponse } from '@/types/api'
import type { Category } from '@/types/product'

const emptyForm: CategoryInput = { name: '', description: '', active: true }

interface CategoryManagerDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function CategoryManagerDialog({ open, onOpenChange }: CategoryManagerDialogProps) {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<Category | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState<CategoryInput>(emptyForm)
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null)

  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: listCategories, enabled: open })

  useEffect(() => {
    if (!open) {
      setShowForm(false)
      setEditing(null)
      setDeleteTarget(null)
    }
  }, [open])

  const saveMutation = useMutation({
    mutationFn: () => (editing ? updateCategory(editing.id, form) : createCategory(form)),
    onSuccess: () => {
      toast.success(editing ? 'Category updated' : 'Category created')
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      setShowForm(false)
      setEditing(null)
    },
    onError: (err) => {
      const axiosError = err as AxiosError<BaseResponse<unknown>>
      toast.error(editing ? 'Update failed' : 'Create failed', {
        description: axiosError.response?.data?.error?.message ?? 'Please check the form and try again.',
      })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (category: Category) => deleteCategory(category.id),
    onSuccess: () => {
      toast.success('Category deleted')
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      setDeleteTarget(null)
    },
    onError: (err) => {
      const axiosError = err as AxiosError<BaseResponse<unknown>>
      toast.error('Delete failed', {
        description: axiosError.response?.data?.error?.message ?? 'This category may still have products in it.',
      })
    },
  })

  function startCreate() {
    setEditing(null)
    setForm(emptyForm)
    setShowForm(true)
  }

  function startEdit(category: Category) {
    setEditing(category)
    setForm({ name: category.name, description: category.description ?? '', active: category.active ?? true })
    setShowForm(true)
  }

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Manage categories</DialogTitle>
            <DialogDescription>Create, edit, or remove product categories.</DialogDescription>
          </DialogHeader>

          {showForm ? (
            <div className="grid gap-4">
              <div className="grid gap-2">
                <Label htmlFor="category-name">Name</Label>
                <Input
                  id="category-name"
                  value={form.name}
                  onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="category-description">Description</Label>
                <Textarea
                  id="category-description"
                  rows={3}
                  value={form.description}
                  onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
                />
              </div>
              <div className="grid gap-2">
                <Label>Status</Label>
                <Select
                  value={form.active ? 'active' : 'inactive'}
                  onValueChange={(value) => setForm((current) => ({ ...current, active: value === 'active' }))}
                >
                  <SelectTrigger className="w-full sm:w-48">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="active">Active</SelectItem>
                    <SelectItem value="inactive">Inactive</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => setShowForm(false)}>
                  Cancel
                </Button>
                <Button
                  type="button"
                  disabled={!form.name.trim() || saveMutation.isPending}
                  onClick={() => saveMutation.mutate()}
                >
                  {saveMutation.isPending ? 'Saving…' : 'Save'}
                </Button>
              </div>
            </div>
          ) : (
            <div className="grid gap-3">
              <Button type="button" size="sm" className="w-fit" onClick={startCreate}>
                <Plus /> New category
              </Button>
              <div className="max-h-[50svh] overflow-y-auto rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {categoriesQuery.data?.content.map((category) => (
                      <TableRow key={category.id}>
                        <TableCell className="font-medium">{category.name}</TableCell>
                        <TableCell className="text-muted-foreground">
                          {category.active === false ? 'Inactive' : 'Active'}
                        </TableCell>
                        <TableCell className="text-right">
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon-sm"
                            aria-label="Edit category"
                            onClick={() => startEdit(category)}
                          >
                            <Pencil />
                          </Button>
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon-sm"
                            aria-label="Delete category"
                            onClick={() => setDeleteTarget(category)}
                          >
                            <Trash2 />
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleteTarget !== null} onOpenChange={(next) => !next && setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete “{deleteTarget?.name}”?</AlertDialogTitle>
            <AlertDialogDescription>This can't be undone.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Keep category</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget)}
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
