package compiler;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

final class TypeSpecTest {
    @Test void valueEqualityIncludesDimensions() {
        assertEquals(new TypeSpec(BasicType.INT, List.of(2, 3)),
                new TypeSpec(BasicType.INT, List.of(2, 3)));
        assertNotEquals(new TypeSpec(BasicType.INT, List.of(2)),
                new TypeSpec(BasicType.INT, List.of(3)));
    }

    @Test void assignmentCompatibilityAllowsOnlyExactTypesAndIntToFloatWidening() {
        TypeSpec integer = TypeSpec.scalar(BasicType.INT);
        TypeSpec floating = TypeSpec.scalar(BasicType.FLOAT);
        TypeSpec bool = TypeSpec.scalar(BasicType.BOOLEAN);

        assertTrue(integer.canAssignFrom(integer));
        assertTrue(floating.canAssignFrom(integer));
        assertFalse(integer.canAssignFrom(floating));
        assertFalse(bool.canAssignFrom(integer));
        assertFalse(new TypeSpec(BasicType.INT, List.of(2)).canAssignFrom(integer));
    }

    @Test void indexingReturnsTheRemainingArrayType() {
        TypeSpec matrix = new TypeSpec(BasicType.FLOAT, List.of(2, 3));
        assertEquals(new TypeSpec(BasicType.FLOAT, List.of(3)), matrix.indexed(1));
        assertEquals(TypeSpec.scalar(BasicType.FLOAT), matrix.indexed(2));
        assertThrows(IllegalArgumentException.class, () -> matrix.indexed(3));
    }

    @Test void typeDimensionsAreDefensivelyCopied() {
        var dimensions = new java.util.ArrayList<>(List.of(2));
        TypeSpec type = new TypeSpec(BasicType.INT, dimensions);
        dimensions.set(0, 9);
        assertEquals(List.of(2), type.dims);
        assertThrows(UnsupportedOperationException.class, () -> type.dims.add(3));
    }
}
