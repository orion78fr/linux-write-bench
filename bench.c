
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <getopt.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/mman.h>
#include <signal.h>


/* Taille d'une page en octet (4Ko) */
#define DEFAULT_PAGE_SIZE 4096
/* Taille du fichier en octet (4Go) */
#define DEFAULT_FILE_SIZE 2147483648LL
/* Nom des logs de blktrace */
#define DEFAULT_OUTPUT "bench"

#define DEFAULT_HOLE 5

#define DEFAULT_WRITES 5

#define _XOPEN_SOURCE 700

/**************************************************
  Structs
 ***************************************************/

struct _parameters
{
    int page_size;
    int nb_files;
    long file_size;
    int mode;
    char * output;
    int hole;
    int writes;
    int pourcentage;
};

/**************************************************
  Enums
 ***************************************************/

enum
{
    MODE_SEMISEQ_WRITE,
    MODE_ALTERNATE_RW,
    MODE_ALTERNATE_FILE,
    MODE_MALLOC_PFRA,
    MODE_RANDOM,
    MODE_SEQ_AND_SYNC,
    MODE_RANDOM_FIXED_HOLES,
    MODE_SEQ_FIXED_HOLES,
    MODE_RANSEQ_FIXED_HOLES
};

/**************************************************
  Globals
 ***************************************************/

char *buf;
struct _parameters parameters;
int pid_blktrace;

/**************************************************
  Functions
 ***************************************************/

int     parse_args (int argc, char *argv[]);

void    start_alternate_rw (void);
void    start_semiseq_write (void);
void    start_alternate_file (void);
void    start_malloc_pfra (void);

void    start_blktrace (void);
void    stop_blktrace (void);

void    open_files (int ** fd);
void    close_files (int * fd);
void    read_page (int fd);
void    write_page (int fd);


/*************************************
  Fonction pour parser les arguments
 ****************************************/
int parse_args(int argc, char *argv[])
{
    int c, option_index, returnValue = 0;
    int has_set_mode = 0;
    int has_set_nb_files = 0;

    parameters.page_size = DEFAULT_PAGE_SIZE;
    parameters.file_size = DEFAULT_FILE_SIZE;
    parameters.output    = DEFAULT_OUTPUT;
    parameters.hole = DEFAULT_HOLE;
    parameters.writes = DEFAULT_WRITES;

    static struct option long_options[] = {
        {"page-size", required_argument, 0, 'p'},
        {"file-size", required_argument, 0, 's'},
        {"mode", required_argument, 0, 'm'},
        {"output", required_argument, 0, 'o'},
        {"nb-files", required_argument, 0, 'f'},
        {0, 0, 0, 0}
    };

    while ((c = getopt_long(argc, argv, "p:s:m:f:o:h:w:r:", long_options, &option_index)) != -1)
    {
        switch (c)
        {
            case 0:
                /* Flag option */
                break;
            case 'o':
                parameters.output = strdup(optarg);
                break;
            case 'r':
                parameters.pourcentage = atoi(optarg);
                break;
            case 'p':
                parameters.page_size = atoi(optarg);
                break;
            case 'h':
                parameters.hole = atoi(optarg);
                break;
            case 'w':
                parameters.writes = atoi(optarg);
                break;
            case 's':
                /* 1024 pour les kilos, 1024 pour les megas
                   La taille est donc en bytes
                 */
                parameters.file_size = atol(optarg)*1024*1024;
                break;
            case 'f':
                has_set_nb_files = 1;
                if (atoi(optarg) >= 1)
                {
                    parameters.nb_files = atoi(optarg);
                }
                else
                {
                    fprintf(stderr, "The numer of files must be greater or equals to 1\n");
                    returnValue++;
                }
                break;
            case 'm':
                has_set_mode = 1;
                if(strcmp("semiseq_write", optarg) == 0){
                    parameters.mode = MODE_SEMISEQ_WRITE;
                } else if(strcmp("alternate_rw", optarg) == 0){
                    parameters.mode = MODE_ALTERNATE_RW;
                } else if(strcmp("alternate_file", optarg) == 0){
                    parameters.mode = MODE_ALTERNATE_FILE;
                } else if(strcmp("malloc_pfra", optarg) == 0){
                    parameters.mode = MODE_MALLOC_PFRA;
                } else if(strcmp("random", optarg) == 0){
                    parameters.mode = MODE_RANDOM;
                } else if(strcmp("seq_and_sync", optarg) == 0){
                    parameters.mode = MODE_SEQ_AND_SYNC;
                } else if(strcmp("random_fixed_holes", optarg) == 0){
                    parameters.mode = MODE_RANDOM_FIXED_HOLES;
                } else if(strcmp("seq_fixed_holes", optarg) == 0){
                    parameters.mode = MODE_SEQ_FIXED_HOLES;
                } else if(strcmp("ranseq_fixed_holes", optarg) == 0){
                    parameters.mode = MODE_RANSEQ_FIXED_HOLES;
                } else {
                    fprintf(stderr, "Mode %s does not exist\n", optarg);
                    returnValue++;
                }
                break;
            case '?':
                /* Erreur, déjà affiché par getopt */
                returnValue++;
                break;
            default:
                fprintf(stderr, "Something went wrong in parameter handling!\n");
                returnValue++;
        }
    }
    if(!has_set_nb_files)
    {
        fprintf(stderr, "Nb_files not set\n");
        returnValue++;
    }
    if(!has_set_mode)
    {
        fprintf(stderr, "Mode not set\n");
        returnValue++;
    }
    return returnValue;
}


/****************************************
  Fonctions des différents benchs
 ***************************************/
void start_alternate_rw(void)
{
    int nb_pages = parameters.file_size / parameters.page_size;
    int * fd;
   
    start_blktrace(); 
    open_files(&fd);

    while(nb_pages)
    {
        read_page(fd[0]);
        write_page(fd[1]);
        nb_pages--;
    }

    close_files(fd);
    stop_blktrace();
}

void start_semiseq_write(void)
{
    int i, offset;
    int max = parameters.file_size / parameters.page_size;
    int * fd;

    start_blktrace();
    open_files(&fd);

    for (i=0; i<parameters.nb_files && fork(); i++);

    srand(getpid());
    for(i=0;i<max;i++)
    {
        if(i==0)
        {
            offset = (int)((rand() % parameters.file_size)/4096)*4096;
            lseek(fd[0], offset, SEEK_SET);
        }
        write_page(fd[i]);
    }

    if(i!=parameters.nb_files)
        exit(0);

    close_files(fd);
    stop_blktrace();
}

void init_tab(int * array, int size)
{
	int i;
	for(i=0; i<size; i++)
	{
		array[i] = i;
	}
}

void shuffle(int * array, int size)
{
	int i;
	srand(getpid());
	for (i=0; i<size-1; i++)
	{
		int j = i + rand() % (size - i + 1);
		int t = array[j];
		array[j] = array[i];
		array[i] = t;
	}
}

void start_random(void)
{
	int nb_pages = (parameters.file_size / parameters.page_size) - 1;
	int fd, i;
	int order[nb_pages];
	int fin = (float)(nb_pages/100)*parameters.pourcentage;

	init_tab(order, nb_pages);
	shuffle(order, nb_pages);

	fd = open("file.1", O_RDWR);
	start_blktrace();

	for(i=0; i<fin; i++)
	{
		lseek(fd, order[i]*parameters.page_size, SEEK_SET);
		write_page(fd);
	}

	fdatasync(fd);
	//sync();
	close(fd);
	stop_blktrace();
}



void start_seq_and_sync(void)
{
	int nb_pages = (parameters.file_size / parameters.page_size) - 1;
	int N = parameters.nb_files-1;
	int fd[N+1], pid[N], i;
	char filename[32];

	start_blktrace();
	fd[0] = open("file.1", O_RDWR);
	for(i=1; i<N; i++)
	{
		memset(filename, 0, 32);
		sprintf(filename, "file.%d", i+1);
		fd[i] = open(filename, O_RDWR|O_SYNC);
	}

	for(i=0; i<N && (pid[i] = fork()) > 0; i++);

	if(i == N)
	{
		while(nb_pages)
		{
			write_page(fd[0]);
			nb_pages--;
		}
		sync();
		sleep(2);
		for(i=0; i<N; i++)
			kill(pid[i], SIGINT);
	}
	else
	{
		srand(getpid());
		while(1)
		{
			write_page(fd[i+1]);
			int usec = rand()%900000 + 100000;
			usleep(usec);
		}
	}

	for(i=0; i<N; i++)
		close(fd[i]);
	stop_blktrace();
}

void start_alternate_file(void)
{
	int i;
	int nb_pages = parameters.file_size / parameters.page_size;
	int * fd;

	start_blktrace();
	open_files(&fd);

	while (nb_pages)
	{
		for(i=0; i<parameters.nb_files; i++)
		{
			write_page(fd[i]);
		}
		nb_pages--;
	}

	close_files(fd);
	stop_blktrace();
}


void start_malloc_pfra(void)
{
	int i;
	int nb_pages = parameters.file_size / parameters.page_size;
	int fd;
	int order[nb_pages];
	int nb_mallocs = 1775;
	int malloc_kb = 1024;
	void * ptr[nb_mallocs];

	init_tab(order, nb_pages);
	shuffle(order, nb_pages);

	fd = open("file.1", O_RDWR);

	start_blktrace();
	for(i=0; i<nb_pages; i++)
	{
		lseek(fd, order[i]*parameters.page_size, SEEK_SET);
		write_page(fd);
	}

	for(i=0; i<nb_mallocs; i++)
	{
		ptr[i] = malloc(malloc_kb*1024);
		memset(ptr[i], 0, malloc_kb*1024);
	}

	fdatasync(fd);	
	close(fd);
	stop_blktrace();
}

/*
 * Creates a tab @tab filled with long starting at @start and ending at @end,
 * with holes at interval @interval with size @size
 */
int getHoleTab(int **tab, int start, int end, int interval, int size)
{
	int nbInter = (end - start + 1)/(interval + size);
	int reste = (end - start + 1)%(interval + size);
	int tabSize = (end - start + 1) - (nbInter * size);
	int i, j=0, current = start;

	if(reste > interval){
		tabSize -= (reste - interval);
	}

	*tab = malloc(tabSize * sizeof(int));

	for(i = 0; i < tabSize; i++){
		(*tab)[i] = current;
		j++;
		if(j == interval){
			j = 0;
			current += size;
		}
		current ++;
	}
	return tabSize;
}

void start_random_fixed_holes(void){
	int nb_pages = (parameters.file_size / parameters.page_size) - 1;
	int fd, i;
	int *order;
	int size;

	/* Trou de 1 tout les 3 */
	size = getHoleTab(&order, 0, nb_pages, parameters.writes, parameters.hole);
	shuffle(order, size);


	start_blktrace();
	fd = open("file.1", O_RDWR);

	for(i=0; i<size; i++)
	{
		lseek(fd, order[i]*parameters.page_size, SEEK_SET);
		write_page(fd);
	}

	fdatasync(fd);
	//sync();
	close(fd);
	stop_blktrace();
}

void start_seq_fixed_holes(void)
{
	int nb_pages = (parameters.file_size / parameters.page_size) - 1;
	int * order;
	int size, fd, i;

	size = getHoleTab(&order, 0, nb_pages, parameters.writes, parameters.hole);

	fd = open("file.1", O_RDWR);
	start_blktrace();

	for(i=0; i<size; i++)
	{
		lseek(fd, order[i]*parameters.page_size, SEEK_SET);
		write_page(fd);
	}	

	fdatasync(fd);
	close(fd);
	stop_blktrace();
}

void start_ranseq_fixed_holes(void)
{
	int nb_pages = (parameters.file_size / parameters.page_size) - 1;
	int size = nb_pages/(parameters.hole + parameters.writes), fd, i, j;
	int order[size];

	init_tab(order, size);
	shuffle(order, size);

	fd = open("file.1", O_RDWR);
	start_blktrace();

	for(i=0; i<size; i++)
	{
		lseek(fd, order[i]*parameters.page_size*(parameters.writes+parameters.hole), SEEK_SET);
		for(j=0; j<parameters.writes; j++)
		{
			write_page(fd);
		}
	}	

	fdatasync(fd);
	close(fd);
	stop_blktrace();
}

/****************************************
  Fonction pour lancer/arreter blktrace
 ******************************************/
void start_blktrace()
{
	if ((pid_blktrace = fork()) == 0)
	{
		execl("/usr/sbin/blktrace", "blktrace", "-d", "/dev/sda", "-o", parameters.output, "-b", "1024", "-n", "8", NULL);
		printf("la merde\n");
	}

	//Pour être sur que blktrace est lancer avant le retour dans le bench
	sleep(1);
}

void stop_blktrace(void)
{
	kill(pid_blktrace, SIGINT);
}

/***************************************
  Fonctions pour manipuler les fichiers
 *****************************************/
void open_files (int ** fd)
{
	int i;
	*fd = malloc(sizeof(int)*parameters.nb_files);
	char filename[64];

	for (i=0; i<parameters.nb_files; i++)
	{
		memset(filename, 0, 64);
		sprintf(filename, "file.%d", i+1);
		(*fd)[i] = open((const char *)filename, O_RDWR);
		if ((*fd)[i] < 0)
		{
			perror("Erreur : open file for bench\n");
			exit(1);
		}
	}
}

void close_files (int * fd)
{
	int i;
	for (i=0; i<parameters.nb_files; i++)
	{
		fdatasync(fd[i]);
		close(fd[i]);
	}
}

void read_page(int fd)
{
	read(fd, buf, parameters.page_size);
}

void write_page(int fd)
{
	write(fd, buf, parameters.page_size);
}



/************************************
  Main
 ***************************************/
int main(int argc, char *argv[])
{
	if(argc == 1 || parse_args(argc, argv) != 0)
	{
		printf("Usage :\n");
		printf("\t%s [-p page_size] [-s file_size(mbs)] [-o logs_output] -m mode -f filename\n", argv[0]);
		printf("\tAvailable mods : semiseq_write, alternate_rw, alternate_file\n");
		exit(EXIT_SUCCESS);
	}

	buf = malloc(parameters.page_size);

	if(parameters.mode == MODE_ALTERNATE_FILE)
	{
		start_alternate_file();
	}
	else if(parameters.mode == MODE_SEMISEQ_WRITE)
	{
		start_semiseq_write();
	}
	else if(parameters.mode == MODE_MALLOC_PFRA)
	{
		start_malloc_pfra();
	}
	else if(parameters.mode == MODE_RANDOM)
	{
		start_random();
	}
	else if(parameters.mode == MODE_SEQ_AND_SYNC)
	{
		start_seq_and_sync();
	}
	else if(parameters.mode == MODE_RANDOM_FIXED_HOLES){
		start_random_fixed_holes();
	}
	else if(parameters.mode == MODE_SEQ_FIXED_HOLES){
		start_seq_fixed_holes();
	}
	else if(parameters.mode == MODE_RANSEQ_FIXED_HOLES){
		start_ranseq_fixed_holes();
	}
	else
	{
		start_alternate_rw();
	}

	free(buf);
	return EXIT_SUCCESS;
}

