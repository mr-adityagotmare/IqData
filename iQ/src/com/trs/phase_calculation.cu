extern "C"
__global__ void computePhase(
    const float *globalArray,
    double *phase,
    int shift,
    int sweep_points
){
    int i = blockIdx.x * blockDim.x + threadIdx.x;

    int complexSamples = sweep_points * 2;

    if (i < complexSamples) {

        int base = shift + (2 * i);

        float re = globalArray[base];
        float im = globalArray[base + 1];

        double phase_val = atan2((double)re, (double)im);

        phase[i] = phase_val * (180.0 / 3.141592653589793);
    }
}
