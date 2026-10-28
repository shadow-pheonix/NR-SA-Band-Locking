#ifndef SA_LAB_QMI_SA_H
#define SA_LAB_QMI_SA_H
int qmi_sa_run(int argc,char **argv);
#ifdef SA_TESTING
void qmi_sa_set_mock(const char *prefix);
#endif
#endif
