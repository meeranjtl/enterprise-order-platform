import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { AxiosError } from 'axios'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { adjustInventory } from '@/services/inventoryApi'
import { createProduct, listCategories, updateProduct, type ProductInput } from '@/services/productApi'
import type { BaseResponse } from '@/types/api'
import type { Product } from '@/types/product'

const productSchema = z.object({
  sku: z.string().min(1, 'SKU is required').max(64),
  name: z.string().min(1, 'Product name is required').max(150),
  description: z.string().max(1000).optional().or(z.literal('')),
  price: z
    .string()
    .min(1, 'Price is required')
    .refine((v) => Number.isFinite(Number(v)) && Number(v) > 0, 'Price must be greater than zero'),
  stockQuantity: z
    .string()
    .min(1, 'Stock quantity is required')
    .refine((v) => Number.isInteger(Number(v)) && Number(v) >= 0, 'Stock quantity must be a non-negative whole number'),
  categoryId: z.string().min(1, 'Category is required'),
  status: z.string().optional(),
})

type ProductFormValues = z.infer<typeof productSchema>

const emptyValues: ProductFormValues = {
  sku: '',
  name: '',
  description: '',
  price: '',
  stockQuantity: '',
  categoryId: '',
  status: 'ACTIVE',
}

function valuesFromProduct(product: Product): ProductFormValues {
  return {
    sku: product.sku,
    name: product.name,
    description: product.description ?? '',
    price: String(product.price),
    stockQuantity: String(product.stockQuantity),
    categoryId: String(product.categoryId),
    status: product.status,
  }
}

function toPayload(values: ProductFormValues): ProductInput {
  return {
    sku: values.sku,
    name: values.name,
    description: values.description || undefined,
    price: Number(values.price),
    stockQuantity: Number(values.stockQuantity),
    categoryId: Number(values.categoryId),
    status: values.status,
  }
}

interface ProductFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  product?: Product
}

export function ProductFormDialog({ open, onOpenChange, product }: ProductFormDialogProps) {
  const isEditing = product !== undefined
  const queryClient = useQueryClient()

  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: listCategories, enabled: open })

  const form = useForm<ProductFormValues>({
    resolver: zodResolver(productSchema),
    defaultValues: emptyValues,
  })

  useEffect(() => {
    if (open) {
      form.reset(product ? valuesFromProduct(product) : emptyValues)
    }
  }, [open, product, form])

  const mutation = useMutation({
    mutationFn: async (values: ProductFormValues) => {
      if (isEditing) {
        return updateProduct(product.id, toPayload(values))
      }
      const created = await createProduct(toPayload(values))
      // Seed inventory-service's reservable stock pool — it's a separate
      // store from product-service's catalog stockQuantity, and a product
      // with no row here can never be ordered (reservation 404s).
      await adjustInventory({
        productId: created.id,
        quantity: Number(values.stockQuantity),
        reason: 'Initial stock on product creation',
      })
      return created
    },
    onSuccess: () => {
      toast.success(isEditing ? 'Product updated' : 'Product created')
      queryClient.invalidateQueries({ queryKey: ['products'] })
      onOpenChange(false)
    },
    onError: (err) => {
      const axiosError = err as AxiosError<BaseResponse<unknown>>
      toast.error(isEditing ? 'Update failed' : 'Create failed', {
        description: axiosError.response?.data?.error?.message ?? 'Please check the form and try again.',
      })
    },
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Edit product' : 'Add product'}</DialogTitle>
          <DialogDescription>
            {isEditing ? 'Update this product’s catalog details.' : 'Add a new product to the catalog.'}
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
            className="grid max-h-[70svh] gap-4 overflow-y-auto px-1"
            noValidate
          >
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="sku"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>SKU</FormLabel>
                    <FormControl>
                      <Input {...field} disabled={isEditing} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="name"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Name</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description</FormLabel>
                  <FormControl>
                    <Textarea rows={3} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="price"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Price</FormLabel>
                    <FormControl>
                      <Input type="number" min={0.01} step="0.01" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="stockQuantity"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Stock quantity</FormLabel>
                    <FormControl>
                      <Input type="number" min={0} step="1" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="categoryId"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Category</FormLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue placeholder="Select a category" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {categoriesQuery.data?.content.map((category) => (
                          <SelectItem key={category.id} value={String(category.id)}>
                            {category.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {isEditing && (
                <FormField
                  control={form.control}
                  name="status"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Status</FormLabel>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <FormControl>
                          <SelectTrigger className="w-full">
                            <SelectValue />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectItem value="ACTIVE">Active</SelectItem>
                          <SelectItem value="OUT_OF_STOCK">Out of stock</SelectItem>
                          <SelectItem value="INACTIVE">Inactive</SelectItem>
                          <SelectItem value="DISCONTINUED">Discontinued</SelectItem>
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              )}
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Saving…' : 'Save'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
