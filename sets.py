import array
import numpy as np
from IPython import embed


class sperner_family:
    def __init__(self):
        self.alphabet = {}
        self.sets = np.zeros((0, 0)).astype(np.bool_)

    def _index(self, word):
        if word in self.alphabet:
            return self.alphabet[word]
        index = len(self.alphabet)
        self.alphabet[word] = index
        self.sets = np.pad(self.sets, ((0, 0), (0, 1)))
        return index

    def new_row(self):
        return np.zeros((1, self.sets.shape[1])).astype(np.bool_)

    def rowify(self, A):
        for element in A:
            index = self._index(element)
        new_set = self.new_row()
        for element in A:
            index = self._index(element)
            new_set[0, index] = 1
        return new_set

    def add(self, A):
        new_set = self.rowify(A)
        self.sets = np.append(self.sets, new_set, 0)
        if self.sets.shape[0] == 0:
            return

        set_inverse = np.invert(self.sets)
        overlaps = np.matmul(self.sets, set_inverse.transpose())
        diagonal_mask = np.invert(np.eye(*overlaps.shape, dtype=np.bool_))
        subset_mask = overlaps != diagonal_mask
        if True in subset_mask[-1]:
            # new set was subsumed by a previously stored set
            self.sets = self.sets[:-1]
            return

        dropped = []
        for i, row in enumerate(subset_mask[:-1]):
            if True in row:
                dropped.append(i)
        self.sets = np.delete(self.sets, dropped, 0)

    def contains(self, A):
        new_set = self.rowify(A)
        family = np.append(self.sets, new_set, 0)
        if family.shape[0] == 0:
            return False

        set_inverse = np.invert(family)
        overlaps = np.matmul(family, set_inverse.transpose())
        diagonal_mask = np.invert(np.eye(*overlaps.shape, dtype=np.bool_))
        subset_mask = overlaps != diagonal_mask
        return True in subset_mask[-1]
