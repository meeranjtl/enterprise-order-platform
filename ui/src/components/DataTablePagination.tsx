import { Button } from '@/components/ui/button'

interface DataTablePaginationProps {
  /** 0-based current page index, matching Spring Data's Page<T>.number. */
  page: number
  totalPages: number
  totalElements: number
  onPageChange: (page: number) => void
}

export function DataTablePagination({ page, totalPages, totalElements, onPageChange }: DataTablePaginationProps) {
  if (totalElements === 0) {
    return null
  }

  return (
    <div className="flex items-center justify-between border-t pt-3 text-sm text-muted-foreground">
      <span>
        Page {page + 1} of {Math.max(totalPages, 1)} &middot; {totalElements} total
      </span>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
          Previous
        </Button>
        <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
          Next
        </Button>
      </div>
    </div>
  )
}
