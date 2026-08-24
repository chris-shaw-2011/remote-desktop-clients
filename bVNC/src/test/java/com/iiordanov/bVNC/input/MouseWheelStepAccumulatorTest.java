package com.iiordanov.bVNC.input;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MouseWheelStepAccumulatorTest {
    @Test
    public void isolatedSmallMovementsAlwaysProduceOneStep() {
        TouchInputHandlerGeneric.MouseWheelStepAccumulator accumulator =
                new TouchInputHandlerGeneric.MouseWheelStepAccumulator();

        assertEquals(1, accumulator.consume(0.25f, true, 0));
        assertEquals(1, accumulator.consume(0.25f, true, 101));
        assertEquals(-1, accumulator.consume(-0.25f, true, 202));
    }

    @Test
    public void sustainedMovementHonorsFractionalRate() {
        TouchInputHandlerGeneric.MouseWheelStepAccumulator accumulator =
                new TouchInputHandlerGeneric.MouseWheelStepAccumulator();

        assertEquals(1, accumulator.consume(0.25f, true, 0));
        assertEquals(0, accumulator.consume(0.25f, true, 10));
        assertEquals(0, accumulator.consume(0.25f, true, 20));
        assertEquals(0, accumulator.consume(0.25f, true, 30));
        assertEquals(0, accumulator.consume(0.25f, true, 40));
        assertEquals(0, accumulator.consume(0.25f, true, 50));
        assertEquals(0, accumulator.consume(0.25f, true, 60));
        assertEquals(1, accumulator.consume(0.25f, true, 70));
    }

    @Test
    public void directionChangeStartsANewPreciseStep() {
        TouchInputHandlerGeneric.MouseWheelStepAccumulator accumulator =
                new TouchInputHandlerGeneric.MouseWheelStepAccumulator();

        assertEquals(1, accumulator.consume(0.25f, true, 0));
        assertEquals(-1, accumulator.consume(-0.25f, true, 10));
    }
}
